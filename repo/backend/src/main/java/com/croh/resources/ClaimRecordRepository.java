package com.croh.resources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClaimRecordRepository extends JpaRepository<ClaimRecord, Long> {

    long countByResourceIdAndAccountIdAndResultAndCreatedAtAfter(
            Long resourceId, Long accountId, String result, LocalDateTime after);

    long countByResourceIdAndHouseholdKeyAndResultAndCreatedAtAfter(
            Long resourceId, String householdKey, String result, LocalDateTime after);
}
