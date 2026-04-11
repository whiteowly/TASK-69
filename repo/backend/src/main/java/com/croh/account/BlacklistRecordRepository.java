package com.croh.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistRecordRepository extends JpaRepository<BlacklistRecord, Long> {

    List<BlacklistRecord> findByAccountId(Long accountId);

    Optional<BlacklistRecord> findTopByAccountIdOrderByCreatedAtDesc(Long accountId);
}
