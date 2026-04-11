package com.croh.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Long> {

    List<MetricDefinition> findByDomain(String domain);
}
