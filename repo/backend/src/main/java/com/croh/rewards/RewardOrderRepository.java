package com.croh.rewards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RewardOrderRepository extends JpaRepository<RewardOrder, Long> {

    List<RewardOrder> findByAccountId(Long accountId);

    long countByRewardIdAndAccountId(Long rewardId, Long accountId);

    @Query("SELECT o FROM RewardOrder o JOIN RewardItem r ON o.rewardId = r.id WHERE r.organizationId IN :orgIds")
    List<RewardOrder> findByRewardOrgIn(@Param("orgIds") List<String> orgIds);

    @Query("SELECT o FROM RewardOrder o WHERE o.status IN (:statuses) AND o.statusChangedAt < :before")
    List<RewardOrder> findOverdueOrders(@Param("statuses") List<String> statuses,
                                         @Param("before") LocalDateTime before);
}
