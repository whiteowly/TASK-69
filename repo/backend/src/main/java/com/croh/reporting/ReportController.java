package com.croh.reporting;

import com.croh.reporting.dto.MetricDefinitionRequest;
import com.croh.reporting.dto.MetricDefinitionResponse;
import com.croh.reporting.dto.ReportExecuteRequest;
import com.croh.reporting.dto.ReportExecutionResponse;
import com.croh.reporting.dto.ReportTemplateRequest;
import com.croh.reporting.dto.ReportTemplateResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/metric-definitions")
    @RequirePermission(Permission.MANAGE_METRICS)
    public ResponseEntity<List<MetricDefinitionResponse>> listMetricDefinitions() {
        List<MetricDefinitionResponse> list = reportService.listMetricDefinitions().stream()
                .map(m -> new MetricDefinitionResponse(m.getId(), m.getName(), m.getDescription(),
                        m.getQueryTemplate(), m.getDomain(), m.getCreatedBy(), m.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/templates")
    @RequirePermission(Permission.MANAGE_REPORT_TEMPLATES)
    public ResponseEntity<List<ReportTemplateResponse>> listTemplates() {
        List<ReportTemplateResponse> list = reportService.listTemplates().stream()
                .map(t -> new ReportTemplateResponse(t.getId(), t.getName(), t.getDescription(),
                        t.getMetricIds(), t.getDefaultFilters(), t.getOutputFormat(),
                        t.getCreatedBy(), t.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/executions")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<List<ReportExecutionResponse>> listExecutions() {
        SessionAccount actor = getSessionAccount();
        boolean isAdmin = actor.activeRole() == com.croh.security.RoleType.ADMIN;
        List<ReportExecutionResponse> list = reportService.listExecutions(actor.accountId(), isAdmin).stream()
                .map(this::toListResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/metric-definitions")
    @RequirePermission(Permission.MANAGE_METRICS)
    public ResponseEntity<MetricDefinitionResponse> createMetricDefinition(
            @Valid @RequestBody MetricDefinitionRequest request) {
        SessionAccount actor = getSessionAccount();
        MetricDefinition metric = reportService.createMetricDefinition(
                request.name(), request.description(), request.queryTemplate(),
                request.domain(), actor.accountId());
        return ResponseEntity.status(201).body(new MetricDefinitionResponse(
                metric.getId(), metric.getName(), metric.getDescription(),
                metric.getQueryTemplate(), metric.getDomain(),
                metric.getCreatedBy(), metric.getCreatedAt()));
    }

    @PostMapping("/templates")
    @RequirePermission(Permission.MANAGE_REPORT_TEMPLATES)
    public ResponseEntity<ReportTemplateResponse> createTemplate(
            @Valid @RequestBody ReportTemplateRequest request) {
        SessionAccount actor = getSessionAccount();
        ReportTemplate template = reportService.createTemplate(
                request.name(), request.description(), request.metricIds(),
                request.filters(), request.format(), actor.accountId());
        return ResponseEntity.status(201).body(new ReportTemplateResponse(
                template.getId(), template.getName(), template.getDescription(),
                template.getMetricIds(), template.getDefaultFilters(),
                template.getOutputFormat(), template.getCreatedBy(), template.getCreatedAt()));
    }

    @PostMapping("/templates/{id}/execute")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<ReportExecutionResponse> executeReport(
            @PathVariable Long id,
            @RequestBody ReportExecuteRequest request) {
        SessionAccount actor = getSessionAccount();
        ReportExecution execution = reportService.executeReport(
                id, request.filters(), request.format(), actor.accountId());
        return ResponseEntity.status(201).body(toResponse(execution));
    }

    @GetMapping("/executions/{id}/download")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<byte[]> downloadExport(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        ReportExecution execution = reportService.getExport(id);
        if (!execution.getExecutedBy().equals(actor.accountId())) {
            return ResponseEntity.status(403).build();
        }
        if (execution.getExportFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = reportService.readExportFile(execution.getExportFilePath());
        String contentType = execution.getExportFilePath().endsWith(".pdf")
                ? "application/pdf" : "text/csv";
        String filename = execution.getExportFilePath().substring(
                execution.getExportFilePath().lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    @GetMapping("/data-quality")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<Map<String, Object>> getDataQuality(
            @RequestParam(required = false) String domain,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(reportService.getDataQuality(domain, from, to));
    }

    private ReportExecutionResponse toResponse(ReportExecution e) {
        return new ReportExecutionResponse(e.getId(), e.getTemplateId(), e.getFilters(),
                e.getOutputFormat(), e.getStatus(), e.getResultData(),
                e.getExportFilePath(), e.getExecutedBy(), e.getCreatedAt(), e.getCompletedAt());
    }

    private ReportExecutionResponse toListResponse(ReportExecution e) {
        // Minimize payload: exclude resultData from list responses
        return new ReportExecutionResponse(e.getId(), e.getTemplateId(), e.getFilters(),
                e.getOutputFormat(), e.getStatus(), null,
                e.getExportFilePath(), e.getExecutedBy(), e.getCreatedAt(), e.getCompletedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
