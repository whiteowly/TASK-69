package com.croh.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistAppealRepository extends JpaRepository<BlacklistAppeal, Long> {

    List<BlacklistAppeal> findByAccountId(Long accountId);

    List<BlacklistAppeal> findByStatus(String status);

    Optional<BlacklistAppeal> findByIdAndStatus(Long id, String status);
}
