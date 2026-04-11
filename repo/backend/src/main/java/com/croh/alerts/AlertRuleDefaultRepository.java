package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertRuleDefaultRepository extends JpaRepository<AlertRuleDefault, Long> {

    Optional<AlertRuleDefault> findByAlertType(String alertType);
}
