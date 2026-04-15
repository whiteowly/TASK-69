import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/admin/roles/pending             (MANAGE_ROLE_APPROVALS)
 *   POST /api/v1/admin/roles/{membershipId}/decision  (MANAGE_ROLE_APPROVALS)
 *   POST /api/v1/accounts/me/role-requests       (auth, self-service)
 *   GET  /api/v1/accounts/me/roles               (auth, self-service)
 *
 * The negative paths exercise auth/authz enforcement (401/403) so that any
 * regression that opens up admin endpoints to PARTICIPANT users will fail here.
 */
test.describe('Admin roles — pending queue and decisions', () => {
  test('admin can list pending role requests and shape is an array of memberships', async ({ request }) => {
    // Arrange: a fresh user submits a VOLUNTEER role request so the queue has at least one entry.
    const username = uniqueUser('rolereq');
    const csrf = await registerAndLogin(request, username);
    const reqRes = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'VOLUNTEER' },
    });
    expect(reqRes.status()).toBe(201);
    const reqBody = await reqRes.json();
    expect(reqBody.id).toBeGreaterThan(0);
    expect(reqBody.roleType).toBe('VOLUNTEER');
    expect(reqBody.status).toBe('REQUESTED');

    // Act: log in as admin and call /pending.
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/admin/roles/pending');

    // Assert: 200 + array shape + the new request is included.
    expect(res.status()).toBe(200);
    const list = await res.json();
    expect(Array.isArray(list)).toBe(true);
    const ours = list.find((m: any) => m.id === reqBody.id);
    expect(ours).toBeTruthy();
    expect(ours.status).toBe('REQUESTED');
    expect(ours.roleType).toBe('VOLUNTEER');
  });

  test('PARTICIPANT cannot list pending role requests (403)', async ({ request }) => {
    const username = uniqueUser('nopend');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/admin/roles/pending');
    expect(res.status()).toBe(403);
  });

  test('unauthenticated /pending returns 401', async ({ request }) => {
    const res = await request.get('/api/v1/admin/roles/pending');
    expect(res.status()).toBe(401);
  });

  test('admin can approve a pending role request and status flips to APPROVED', async ({ request }) => {
    // Create a fresh request to approve.
    const username = uniqueUser('approveme');
    const csrf = await registerAndLogin(request, username);
    const reqRes = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'PARTICIPANT' },
    });
    expect(reqRes.status()).toBe(201);
    const membershipId = (await reqRes.json()).id;

    // Switch to admin context and approve.
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const decisionRes = await request.post(`/api/v1/admin/roles/${membershipId}/decision`, {
      headers: adminCsrf,
      data: { decision: 'APPROVE', reviewNote: 'looks good' },
    });
    expect(decisionRes.status()).toBe(200);
    const body = await decisionRes.json();
    expect(body.id).toBe(membershipId);
    expect(body.status).toBe('APPROVED');
    expect(body.roleType).toBe('PARTICIPANT');
  });

  test('PARTICIPANT cannot approve role requests (403)', async ({ request }) => {
    const username = uniqueUser('nodec');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/admin/roles/999/decision', {
      headers: csrf,
      data: { decision: 'APPROVE' },
    });
    expect(res.status()).toBe(403);
  });

  test('decision endpoint validates body — missing decision → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/admin/roles/1/decision', {
      headers: adminCsrf,
      data: {},
    });
    // @NotBlank on RoleDecisionRequest.decision triggers 400 from validation.
    expect(res.status()).toBe(400);
  });
});

test.describe('Self-service /accounts/me/role-requests + /roles', () => {
  test('listing my roles returns at least the auto-PARTICIPANT entry after approval', async ({ request }) => {
    const username = uniqueUser('myroles');
    const csrf = await registerAndLogin(request, username);
    // Submit a request and have admin approve it.
    const reqRes = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'PARTICIPANT' },
    });
    const membershipId = (await reqRes.json()).id;
    const adminCsrf = await loginAsSeeded(request, 'admin');
    await request.post(`/api/v1/admin/roles/${membershipId}/decision`, {
      headers: adminCsrf,
      data: { decision: 'APPROVE' },
    });

    // Log back in as the user and read /roles.
    await request.post('/api/v1/auth/login', { data: { username, password: 'TestPass123!' } });
    const rolesRes = await request.get('/api/v1/accounts/me/roles');
    expect(rolesRes.status()).toBe(200);
    const roles = await rolesRes.json();
    expect(Array.isArray(roles)).toBe(true);
    expect(roles.some((r: any) => r.roleType === 'PARTICIPANT' && r.status === 'APPROVED')).toBe(true);
  });

  test('duplicate role request for same role+scope returns 409', async ({ request }) => {
    const username = uniqueUser('duprole');
    const csrf = await registerAndLogin(request, username);
    const first = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'VOLUNTEER' },
    });
    expect(first.status()).toBe(201);
    const dup = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'VOLUNTEER' },
    });
    expect(dup.status()).toBe(409);
  });
});
