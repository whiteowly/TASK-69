package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertRuleOverrideRepository extends JpaRepository<AlertRuleOverride, Long> {

    Optional<AlertRuleOverride> findByAlertTypeAndScopeTypeAndScopeId(
            String alertType, String scopeType, String scopeId);
}
