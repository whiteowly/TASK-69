package com.croh.rewards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FulfillmentExceptionRepository extends JpaRepository<FulfillmentException, Long> {

    List<FulfillmentException> findByOrderId(Long orderId);

    List<FulfillmentException> findByStatus(String status);
}
