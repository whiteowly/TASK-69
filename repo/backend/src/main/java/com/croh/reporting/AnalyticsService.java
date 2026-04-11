package com.croh.reporting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final EntityManager entityManager;

    public AnalyticsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Map<String, Object> getOperationsSummary(LocalDateTime from, LocalDateTime to,
                                                      String orgId) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Registration counts (filterable by org via event.organization_id)
        summary.put("totalRegistrations", countRegistrations(from, to, null, orgId));
        summary.put("approvedRegistrations", countRegistrations(from, to, "APPROVED", orgId));
        summary.put("cancelledRegistrations", countRegistrations(from, to, "CANCELLED", orgId));
        summary.put("waitlistedRegistrations", countRegistrations(from, to, "WAITLISTED", orgId));

        // Claim counts
        summary.put("totalClaims", countClaims(from, to));
        summary.put("allowedClaims", countClaimsByResult(from, to, "ALLOWED"));
        summary.put("deniedClaims", countClaimsByResult(from, to, "DENIED_POLICY"));

        // Order counts
        summary.put("totalOrders", countOrders(from, to));
        summary.put("deliveredOrders", countOrdersByStatus(from, to, "DELIVERED"));
        summary.put("redeemedOrders", countOrdersByStatus(from, to, "REDEEMED"));

        // Completion rates
        long totalRegs = (long) summary.get("totalRegistrations");
        long approvedRegs = (long) summary.get("approvedRegistrations");
        summary.put("registrationApprovalRate",
                totalRegs > 0 ? (double) approvedRegs / totalRegs : 0.0);

        long totalOrders = (long) summary.get("totalOrders");
        long completedOrders = (long) summary.get("deliveredOrders") + (long) summary.get("redeemedOrders");
        summary.put("orderCompletionRate",
                totalOrders > 0 ? (double) completedOrders / totalOrders : 0.0);

        // Staff workload: orders fulfilled (transitioned) per staff member
        summary.put("staffWorkload", getStaffWorkload(from, to));

        // Popular service categories: events grouped by mode
        summary.put("popularCategories", getPopularCategories(from, to, orgId));

        // Retention: accounts with more than one registration in the period
        summary.put("retentionRate", getRetentionRate(from, to));

        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> getStaffWorkload(LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT al.actor_account_id, COUNT(*) as cnt " +
                "FROM audit_log al " +
                "WHERE al.action_type = 'ORDER_TRANSITIONED' " +
                "AND al.timestamp BETWEEN :from AND :to " +
                "AND al.actor_account_id IS NOT NULL " +
                "GROUP BY al.actor_account_id ORDER BY cnt DESC";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", from);
        query.setParameter("to", to);
        List<Object[]> rows = query.getResultList();
        Map<String, Long> workload = new LinkedHashMap<>();
        for (Object[] row : rows) {
            workload.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return workload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> getPopularCategories(LocalDateTime from, LocalDateTime to,
                                                     String orgId) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.mode, COUNT(er.id) as reg_count " +
                "FROM event_registration er " +
                "JOIN event e ON er.event_id = e.id " +
                "WHERE er.created_at BETWEEN :from AND :to");
        if (orgId != null) {
            sql.append(" AND e.organization_id = :orgId");
        }
        sql.append(" GROUP BY e.mode ORDER BY reg_count DESC");
        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (orgId != null) {
            query.setParameter("orgId", orgId);
        }
        List<Object[]> rows = query.getResultList();
        Map<String, Long> categories = new LinkedHashMap<>();
        for (Object[] row : rows) {
            categories.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return categories;
    }

    private double getRetentionRate(LocalDateTime from, LocalDateTime to) {
        // Accounts with >1 registration in the period vs total unique registrants
        String totalSql = "SELECT COUNT(DISTINCT account_id) FROM event_registration " +
                "WHERE created_at BETWEEN :from AND :to";
        String repeatSql = "SELECT COUNT(*) FROM (" +
                "SELECT account_id FROM event_registration " +
                "WHERE created_at BETWEEN :from AND :to " +
                "GROUP BY account_id HAVING COUNT(*) > 1) repeat_users";
        Query totalQ = entityManager.createNativeQuery(totalSql);
        totalQ.setParameter("from", from);
        totalQ.setParameter("to", to);
        long totalUsers = ((Number) totalQ.getSingleResult()).longValue();

        if (totalUsers == 0) return 0.0;

        Query repeatQ = entityManager.createNativeQuery(repeatSql);
        repeatQ.setParameter("from", from);
        repeatQ.setParameter("to", to);
        long repeatUsers = ((Number) repeatQ.getSingleResult()).longValue();

        return (double) repeatUsers / totalUsers;
    }

    private long countRegistrations(LocalDateTime from, LocalDateTime to, String status,
                                     String orgId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM event_registration er");
        if (orgId != null) {
            sql.append(" JOIN event e ON er.event_id = e.id");
        }
        sql.append(" WHERE er.created_at BETWEEN :from AND :to");
        if (status != null) {
            sql.append(" AND er.status = :status");
        }
        if (orgId != null) {
            sql.append(" AND e.organization_id = :orgId");
        }
        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (orgId != null) {
            query.setParameter("orgId", orgId);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countClaims(LocalDateTime from, LocalDateTime to) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM claim_record WHERE created_at BETWEEN :from AND :to");
        query.setParameter("from", from);
        query.setParameter("to", to);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countClaimsByResult(LocalDateTime from, LocalDateTime to, String result) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM claim_record WHERE created_at BETWEEN :from AND :to AND result = :result");
        query.setParameter("from", from);
        query.setParameter("to", to);
        query.setParameter("result", result);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countOrders(LocalDateTime from, LocalDateTime to) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM reward_order WHERE created_at BETWEEN :from AND :to");
        query.setParameter("from", from);
        query.setParameter("to", to);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long countOrdersByStatus(LocalDateTime from, LocalDateTime to, String status) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM reward_order WHERE created_at BETWEEN :from AND :to AND status = :status");
        query.setParameter("from", from);
        query.setParameter("to", to);
        query.setParameter("status", status);
        return ((Number) query.getSingleResult()).longValue();
    }
}
