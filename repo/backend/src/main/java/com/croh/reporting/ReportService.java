package com.croh.reporting;

import com.croh.audit.AuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final MetricDefinitionRepository metricRepository;
    private final ReportTemplateRepository templateRepository;
    private final ReportExecutionRepository executionRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;
    private final ExportService exportService;

    public ReportService(MetricDefinitionRepository metricRepository,
                         ReportTemplateRepository templateRepository,
                         ReportExecutionRepository executionRepository,
                         EntityManager entityManager,
                         AuditService auditService,
                         ExportService exportService) {
        this.metricRepository = metricRepository;
        this.templateRepository = templateRepository;
        this.executionRepository = executionRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.exportService = exportService;
    }

    public List<MetricDefinition> listMetricDefinitions() {
        return metricRepository.findAll();
    }

    public List<ReportTemplate> listTemplates() {
        return templateRepository.findAll();
    }

    public List<ReportExecution> listExecutions(Long actorId, boolean isAdmin) {
        if (isAdmin) {
            return executionRepository.findAll();
        }
        return executionRepository.findByExecutedBy(actorId);
    }

    @Transactional
    public MetricDefinition createMetricDefinition(String name, String description,
                                                     String queryTemplate, String domain,
                                                     Long actorId) {
        MetricDefinition metric = new MetricDefinition();
        metric.setName(name);
        metric.setDescription(description);
        metric.setQueryTemplate(queryTemplate);
        metric.setDomain(domain);
        metric.setCreatedBy(actorId);
        metric.setCreatedAt(LocalDateTime.now());

        return metricRepository.save(metric);
    }

    @Transactional
    public ReportTemplate createTemplate(String name, String description, String metricIds,
                                          String filters, String format, Long actorId) {
        ReportTemplate template = new ReportTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setMetricIds(metricIds);
        template.setDefaultFilters(filters);
        template.setOutputFormat(format != null ? format : "CSV");
        template.setCreatedBy(actorId);
        template.setCreatedAt(LocalDateTime.now());

        return templateRepository.save(template);
    }

    @Transactional
    public ReportExecution executeReport(Long templateId, String filters, String format,
                                          Long actorId) {
        ReportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        ReportExecution execution = new ReportExecution();
        execution.setTemplateId(templateId);
        execution.setFilters(filters);
        execution.setOutputFormat(format != null ? format : template.getOutputFormat());
        execution.setStatus("PENDING");
        execution.setExecutedBy(actorId);
        execution.setCreatedAt(LocalDateTime.now());

        // Parse validated filters from the execution request
        Map<String, String> filterMap = parseFilters(filters != null ? filters : template.getDefaultFilters());

        // Execute real metrics from the template's configured metric IDs
        List<String> headers = List.of("metric_name", "query", "result_value");
        List<List<String>> rows = new ArrayList<>();

        String metricIdsStr = template.getMetricIds();
        if (metricIdsStr != null && !metricIdsStr.isBlank()) {
            for (String idStr : metricIdsStr.split(",")) {
                try {
                    Long metricId = Long.parseLong(idStr.trim());
                    metricRepository.findById(metricId).ifPresent(metric -> {
                        String queryTemplate = metric.getQueryTemplate();
                        try {
                            // Execute the metric query with validated filter bindings
                            Query query = entityManager.createNativeQuery(queryTemplate);
                            bindFilters(query, queryTemplate, filterMap);
                            Object result = query.getSingleResult();
                            rows.add(List.of(metric.getName(), queryTemplate, String.valueOf(result)));
                        } catch (Exception e) {
                            rows.add(List.of(metric.getName(), queryTemplate, "ERROR: " + e.getMessage()));
                        }
                    });
                } catch (NumberFormatException ignored) {
                    // skip invalid metric IDs
                }
            }
        }

        if (rows.isEmpty()) {
            rows.add(List.of("(no metrics)", "", "0"));
        }

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");
        for (List<String> row : rows) {
            csv.append(String.join(",", row)).append("\n");
        }

        // Write to local file artifact
        String exportPath;
        String outputFormat = execution.getOutputFormat();
        if ("PDF".equalsIgnoreCase(outputFormat)) {
            exportPath = exportService.exportPdf("Report: " + template.getName(), headers, rows, "report-" + template.getId());
        } else {
            exportPath = exportService.exportCsv(csv.toString(), "report-" + template.getId());
        }

        execution.setResultData(csv.toString());
        execution.setExportFilePath(exportPath);
        execution.setStatus("COMPLETED");
        execution.setCompletedAt(LocalDateTime.now());

        ReportExecution saved = executionRepository.save(execution);

        auditService.log(actorId, null, "REPORT_EXPORTED",
                "ReportExecution", saved.getId().toString(),
                null, "COMPLETED", null, null);

        return saved;
    }

    public ReportExecution getExport(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Report execution not found: " + executionId));
    }

    public byte[] readExportFile(String relativePath) {
        return exportService.readExportFile(relativePath);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataQuality(String domain, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> quality = new LinkedHashMap<>();

        if ("events".equals(domain) || domain == null) {
            quality.put("events_missing_description", missingRateMetric("event", "description", from, to));
            quality.put("events_missing_location", missingRateMetric("event", "location", from, to));
        }

        if ("resources".equals(domain) || domain == null) {
            quality.put("resources_missing_description", missingRateMetric("resource_item", "description", from, to));
        }

        if ("rewards".equals(domain) || domain == null) {
            quality.put("rewards_missing_description", missingRateMetric("reward_item", "description", from, to));
        }

        // Duplicate detection counts
        if ("verification".equals(domain) || domain == null) {
            quality.put("credential_duplicates", countDuplicates(from, to));
        }

        // Anomaly distribution: accounts bucketed by failed login attempt ranges
        if ("accounts".equals(domain) || domain == null) {
            quality.put("failed_login_anomaly_distribution", failedLoginDistribution(from, to));
        }

        return quality;
    }

    /**
     * Returns a missing-rate metric: { count, total, rate } for a nullable field.
     */
    private Map<String, Object> missingRateMetric(String table, String field,
                                                    LocalDateTime from, LocalDateTime to) {
        long missing = countNullField(table, field, from, to);
        long total = countTotal(table, from, to);
        double rate = total > 0 ? (double) missing / total : 0.0;
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("missing", missing);
        metric.put("total", total);
        metric.put("rate", Math.round(rate * 10000.0) / 10000.0);
        return metric;
    }

    /**
     * Returns anomaly distribution for failed login attempts: bucketed counts.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> failedLoginDistribution(LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT " +
                "SUM(CASE WHEN failed_login_attempts BETWEEN 1 AND 4 THEN 1 ELSE 0 END) as low, " +
                "SUM(CASE WHEN failed_login_attempts BETWEEN 5 AND 9 THEN 1 ELSE 0 END) as medium, " +
                "SUM(CASE WHEN failed_login_attempts >= 10 THEN 1 ELSE 0 END) as high, " +
                "COUNT(*) as total " +
                "FROM account WHERE updated_at BETWEEN :from AND :to";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", from);
        query.setParameter("to", to);
        Object[] row = (Object[]) query.getSingleResult();
        Map<String, Object> dist = new LinkedHashMap<>();
        dist.put("low_1_4", ((Number) row[0]).longValue());
        dist.put("medium_5_9", ((Number) row[1]).longValue());
        dist.put("high_10_plus", ((Number) row[2]).longValue());
        dist.put("total_accounts", ((Number) row[3]).longValue());
        return dist;
    }

    private long countDuplicates(LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT COUNT(*) FROM organization_credential_document " +
                "WHERE duplicate_flag = true AND created_at BETWEEN :from AND :to";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", from);
        query.setParameter("to", to);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countTotal(String table, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE created_at BETWEEN :from AND :to";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", from);
        query.setParameter("to", to);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countNullField(String table, String field, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT COUNT(*) FROM " + table +
                " WHERE " + field + " IS NULL AND created_at BETWEEN :from AND :to";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", from);
        query.setParameter("to", to);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Parses a JSON filters string into a map. Returns empty map on null/invalid input.
     */
    private Map<String, String> parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(filtersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Binds filter values to named parameters found in the query template.
     * Supports :from, :to (as LocalDateTime), :organizationId, :stationId.
     */
    private void bindFilters(Query query, String queryTemplate, Map<String, String> filterMap) {
        if (queryTemplate.contains(":from") && filterMap.containsKey("from")) {
            query.setParameter("from", LocalDateTime.parse(filterMap.get("from") + (filterMap.get("from").contains("T") ? "" : "T00:00:00")));
        }
        if (queryTemplate.contains(":to") && filterMap.containsKey("to")) {
            query.setParameter("to", LocalDateTime.parse(filterMap.get("to") + (filterMap.get("to").contains("T") ? "" : "T23:59:59")));
        }
        if (queryTemplate.contains(":organizationId") && filterMap.containsKey("organizationId")) {
            query.setParameter("organizationId", filterMap.get("organizationId"));
        }
        if (queryTemplate.contains(":stationId") && filterMap.containsKey("stationId")) {
            query.setParameter("stationId", filterMap.get("stationId"));
        }
    }
}
