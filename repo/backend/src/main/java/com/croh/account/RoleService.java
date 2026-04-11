package com.croh.account;

import com.croh.audit.AuditService;
import com.croh.common.DuplicateException;
import com.croh.security.Permission;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import com.croh.security.SessionAuthenticationFilter;
import com.croh.verification.OrganizationCredentialDocumentRepository;
import com.croh.verification.PersonVerificationRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RoleService {

    private final RoleMembershipRepository roleMembershipRepository;
    private final AccountRepository accountRepository;
    private final PersonVerificationRepository personVerificationRepository;
    private final OrganizationCredentialDocumentRepository orgDocRepository;
    private final AuditService auditService;

    public RoleService(RoleMembershipRepository roleMembershipRepository,
                       AccountRepository accountRepository,
                       PersonVerificationRepository personVerificationRepository,
                       OrganizationCredentialDocumentRepository orgDocRepository,
                       AuditService auditService) {
        this.roleMembershipRepository = roleMembershipRepository;
        this.accountRepository = accountRepository;
        this.personVerificationRepository = personVerificationRepository;
        this.orgDocRepository = orgDocRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RoleMembership requestRole(Long accountId, String role, String scopeType, String scopeId) {
        List<RoleMembership> existing = roleMembershipRepository.findByAccountId(accountId);
        for (RoleMembership m : existing) {
            if (m.getRoleType().equals(role)
                    && Objects.equals(m.getScopeId(), scopeId)
                    && m.getStatus() != RoleMembership.RoleMembershipStatus.DENIED
                    && m.getStatus() != RoleMembership.RoleMembershipStatus.REVOKED) {
                throw new DuplicateException("A role request for this role and scope already exists");
            }
        }

        RoleMembership membership = new RoleMembership();
        membership.setAccountId(accountId);
        membership.setRoleType(role);
        membership.setScopeId(scopeId);
        membership.setStatus(RoleMembership.RoleMembershipStatus.REQUESTED);
        membership.setCreatedAt(LocalDateTime.now());
        membership.setUpdatedAt(LocalDateTime.now());

        RoleMembership saved = roleMembershipRepository.save(membership);

        auditService.log(accountId, null, "ROLE_REQUESTED",
                "RoleMembership", saved.getId().toString(),
                null, "REQUESTED", role, null);

        return saved;
    }

    public List<RoleMembership> listRoles(Long accountId) {
        return roleMembershipRepository.findByAccountId(accountId);
    }

    @Transactional
    public SessionAccount switchRole(Long accountId, String role, String scopeId,
                                     SessionAccount currentSession, HttpSession httpSession) {
        List<RoleMembership> approved = roleMembershipRepository.findByAccountIdAndStatus(
                accountId, RoleMembership.RoleMembershipStatus.APPROVED);

        RoleMembership match = null;
        for (RoleMembership m : approved) {
            if (m.getRoleType().equals(role) && Objects.equals(m.getScopeId(), scopeId)) {
                match = m;
                break;
            }
        }

        if (match == null) {
            throw new IllegalStateException("No approved role membership found for role=" + role + " scopeId=" + scopeId);
        }

        // Check verification prerequisites for the target role
        checkVerificationPrerequisites(accountId, role);

        RoleType roleType = RoleType.valueOf(role);
        Set<Permission> permissions = RolePermissions.getPermissions(roleType);

        SessionAccount newSession = new SessionAccount(
                currentSession.accountId(), currentSession.username(),
                roleType, permissions, currentSession.accountStatus());

        httpSession.setAttribute(SessionAuthenticationFilter.SESSION_ACCOUNT_KEY, newSession);

        auditService.log(accountId, role, "ROLE_SWITCHED",
                "SessionAccount", accountId.toString(),
                currentSession.activeRole().name(), role, null, null);

        return newSession;
    }

    public List<RoleMembership> listPendingRequests() {
        return roleMembershipRepository.findByStatus(RoleMembership.RoleMembershipStatus.REQUESTED);
    }

    @Transactional
    public RoleMembership decideRole(Long membershipId, String decision, String reviewNote,
                                     Long actorId, String actorRole) {
        // Service-layer enforcement: only ADMIN role can decide role approvals
        if (!"ADMIN".equals(actorRole)) {
            throw new SecurityException("Only ADMIN can approve or deny role requests");
        }
        if (!"APPROVE".equals(decision) && !"DENY".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or DENY");
        }

        RoleMembership membership = roleMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Role membership not found: " + membershipId));

        // On approval, check verification prerequisites for the requested role
        if ("APPROVE".equals(decision)) {
            checkVerificationPrerequisites(membership.getAccountId(), membership.getRoleType());
        }

        String beforeStatus = membership.getStatus().name();
        RoleMembership.RoleMembershipStatus newStatus = "APPROVE".equals(decision)
                ? RoleMembership.RoleMembershipStatus.APPROVED
                : RoleMembership.RoleMembershipStatus.DENIED;

        membership.setStatus(newStatus);
        membership.setUpdatedAt(LocalDateTime.now());

        RoleMembership saved = roleMembershipRepository.save(membership);

        auditService.log(actorId, actorRole, "ROLE_DECISION",
                "RoleMembership", saved.getId().toString(),
                beforeStatus, newStatus.name(), decision, null);

        return saved;
    }

    /**
     * Checks verification prerequisites for a role.
     * - VOLUNTEER: requires approved person verification
     * - ORG_OPERATOR: requires approved person verification AND at least one approved org credential
     * - PARTICIPANT: no prerequisites
     * - ADMIN: at admin discretion (no automatic prerequisite)
     */
    private void checkVerificationPrerequisites(Long accountId, String role) {
        if ("VOLUNTEER".equals(role) || "ORG_OPERATOR".equals(role)) {
            boolean personVerified = personVerificationRepository.findByAccountId(accountId)
                    .map(pv -> "APPROVED".equals(pv.getStatus()))
                    .orElse(false);
            if (!personVerified) {
                throw new IllegalStateException(
                        "Person verification must be approved before " + role + " role can be granted");
            }
        }

        if ("ORG_OPERATOR".equals(role)) {
            // Enforce ORGANIZATION account type
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
            if (account.getAccountType() != Account.AccountType.ORGANIZATION) {
                throw new SecurityException(
                        "Only ORGANIZATION accounts can hold the ORG_OPERATOR role");
            }

            boolean hasApprovedCredential = orgDocRepository.findByAccountId(accountId).stream()
                    .anyMatch(doc -> "APPROVED".equals(doc.getStatus()));
            if (!hasApprovedCredential) {
                throw new IllegalStateException(
                        "At least one organization credential must be approved before ORG_OPERATOR role can be granted");
            }
        }
    }
}
