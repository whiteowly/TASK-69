package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderNoteRepository extends JpaRepository<WorkOrderNote, Long> {

    List<WorkOrderNote> findByWorkOrderId(Long workOrderId);
}
