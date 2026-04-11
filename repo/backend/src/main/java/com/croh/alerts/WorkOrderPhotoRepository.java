package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderPhotoRepository extends JpaRepository<WorkOrderPhoto, Long> {

    List<WorkOrderPhoto> findByWorkOrderId(Long workOrderId);
}
