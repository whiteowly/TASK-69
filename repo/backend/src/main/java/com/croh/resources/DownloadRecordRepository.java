package com.croh.resources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface DownloadRecordRepository extends JpaRepository<DownloadRecord, Long> {

    List<DownloadRecord> findByResourceIdAndAccountId(Long resourceId, Long accountId);

    long countByResourceIdAndAccountIdAndResultAndCreatedAtAfter(
            Long resourceId, Long accountId, String result, LocalDateTime after);

    long countByResourceIdAndAccountIdAndFileVersionAndResultAndCreatedAtAfter(
            Long resourceId, Long accountId, String fileVersion, String result, LocalDateTime after);
}
