package com.croh.resources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsagePolicyRepository extends JpaRepository<UsagePolicy, Long> {
}
