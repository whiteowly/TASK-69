package com.croh.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    List<EventRegistration> findByEventId(Long eventId);

    List<EventRegistration> findByAccountId(Long accountId);

    List<EventRegistration> findByEventIdAndStatus(Long eventId, String status);

    long countByEventIdAndStatus(Long eventId, String status);

    List<EventRegistration> findByStatus(String status);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT er.* FROM event_registration er JOIN event e ON er.event_id = e.id " +
                "WHERE er.status = :status AND e.organization_id IN (:orgIds)",
        nativeQuery = true)
    List<EventRegistration> findByStatusAndEventOrganizationIdIn(
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("orgIds") List<String> orgIds);
}
