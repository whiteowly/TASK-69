package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    List<WorkOrder> findByStatus(String status);

    List<WorkOrder> findByOrganizationIdIn(List<String> organizationIds);

    List<WorkOrder> findByOrganizationIdInAndStatus(List<String> organizationIds, String status);
}
