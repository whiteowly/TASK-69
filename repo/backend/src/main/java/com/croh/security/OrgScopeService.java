package com.croh.security;

import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reusable helper for organization-scope authorization checks.
 *
 * All enforcement is object-centric: privileged objects carry an explicit
 * {@code organizationId} and authorization is checked against that value,
 * never inferred from the object creator's current role memberships.
 */
@Service
public class OrgScopeService {

    private final RoleMembershipRepository roleMembershipRepository;

    public OrgScopeService(RoleMembershipRepository roleMembershipRepository) {
        this.roleMembershipRepository = roleMembershipRepository;
    }

    /**
     * Returns the list of organization scope IDs the actor has approved
     * ORG_OPERATOR memberships for.
     */
    public List<String> getOrgScopes(Long actorId) {
        return roleMembershipRepository.findByAccountIdAndStatus(
                        actorId, RoleMembership.RoleMembershipStatus.APPROVED).stream()
                .filter(m -> "ORG_OPERATOR".equals(m.getRoleType()))
                .map(RoleMembership::getScopeId)
                .filter(s -> s != null)
                .toList();
    }

    /**
     * Enforces that the actor is authorized for the given object-bound
     * organization ID.  ADMINs always pass.  For any other role, the actor
     * must hold an approved ORG_OPERATOR membership whose scope_id matches
     * {@code objectOrgId}.
     *
     * <p>Fail-safe: if {@code objectOrgId} is null (legacy data without org
     * binding), non-admin access is rejected.
     *
     * @throws SecurityException if the actor is not authorized
     */
    public void enforceOrgScope(Long actorId, String actorRole, String objectOrgId) {
        if ("ADMIN".equals(actorRole)) {
            return;
        }
        if (objectOrgId == null) {
            throw new SecurityException(
                    "Object has no organization binding; non-admin access denied");
        }
        List<String> actorScopes = getOrgScopes(actorId);
        if (!actorScopes.contains(objectOrgId)) {
            throw new SecurityException(
                    "Not authorized for organization: " + objectOrgId);
        }
    }
}
