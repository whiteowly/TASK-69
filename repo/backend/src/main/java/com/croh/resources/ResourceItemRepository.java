package com.croh.resources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {

    List<ResourceItem> findByStatus(String status);
}
