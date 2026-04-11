package com.croh.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostIncidentReviewRepository extends JpaRepository<PostIncidentReview, Long> {

    Optional<PostIncidentReview> findByWorkOrderId(Long workOrderId);
}
