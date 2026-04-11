package com.croh.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleMembershipRepository extends JpaRepository<RoleMembership, Long> {

    List<RoleMembership> findByAccountId(Long accountId);

    List<RoleMembership> findByAccountIdAndStatus(Long accountId, RoleMembership.RoleMembershipStatus status);

    List<RoleMembership> findByStatus(RoleMembership.RoleMembershipStatus status);

    boolean existsByAccountIdAndRoleTypeAndScopeIdAndStatus(
            Long accountId, String roleType, String scopeId, RoleMembership.RoleMembershipStatus status);
}
