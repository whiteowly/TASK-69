package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    @Query("SELECT e FROM AlertEvent e WHERE e.alertType = :alertType " +
           "AND e.scopeType = :scopeType AND e.scopeId = :scopeId " +
           "AND e.suppressed = false AND e.createdAt > :after " +
           "ORDER BY e.createdAt DESC")
    Optional<AlertEvent> findLatestNonSuppressed(
            @Param("alertType") String alertType,
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("after") LocalDateTime after);

    long countByAlertTypeAndScopeTypeAndScopeIdAndSuppressedFalseAndCreatedAtAfter(
            String alertType, String scopeType, String scopeId, LocalDateTime after);

    /**
     * Returns all events for the given alert type and scope created after the specified time,
     * ordered chronologically (oldest first). Used by sustained-duration evaluation to walk
     * the event timeline and identify unbroken threshold-exceeding streaks.
     */
    List<AlertEvent> findByAlertTypeAndScopeTypeAndScopeIdAndCreatedAtAfterOrderByCreatedAtAsc(
            String alertType, String scopeType, String scopeId, LocalDateTime after);
}
