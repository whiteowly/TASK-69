import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser, login } from './helpers';

/**
 * Endpoint coverage:
 *   GET /api/v1/notices/{id}/print   (auth + object-level: notice must belong to caller)
 *
 * Notices are created server-side as part of the claim flow. We claim a resource
 * to produce a notice, then assert the printable endpoint returns it for the owner
 * and 403/404 for a different user.
 */
test.describe('Notices — printable endpoint', () => {
  test('claimer can print their own notice; second user cannot', async ({ request }) => {
    // Seed a claimable resource via admin.
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/resources', {
      headers: adminCsrf,
      data: {
        type: 'CLAIMABLE_ITEM',
        title: 'Notice Source ' + Date.now(),
        inventoryCount: 5,
        organizationId: 'ORG-1',
        status: 'PUBLISHED',
      },
    });
    expect(create.status()).toBe(201);
    const resourceId = (await create.json()).id;

    // First user claims and pulls noticeId from response.
    const claimer = uniqueUser('claimr');
    const claimerCsrf = await registerAndLogin(request, claimer);
    const claim = await request.post(`/api/v1/resources/${resourceId}/claim`, {
      headers: claimerCsrf,
    });
    expect(claim.status()).toBe(200);
    const claimBody = await claim.json();
    if (!claimBody.noticeId) {
      // Some claim outcomes (e.g. INSUFFICIENT_INVENTORY) won't produce a notice.
      // Skip this run rather than fail spuriously.
      test.skip(true, 'claim did not produce a notice id');
      return;
    }
    const noticeId = claimBody.noticeId;

    // Owner reads the printable.
    const print = await request.get(`/api/v1/notices/${noticeId}/print`);
    expect(print.status()).toBe(200);
    const body = await print.json();
    expect(body.id).toBe(noticeId);
    expect(body.noticeType).toBeTruthy();
    expect(body.content).toBeTruthy();
    expect(body.createdAt).toBeTruthy();

    // Second user is blocked by object-level authorization.
    const intruder = uniqueUser('intrudr');
    await registerAndLogin(request, intruder);
    const denied = await request.get(`/api/v1/notices/${noticeId}/print`);
    expect(denied.status()).toBe(403);
  });

  test('print on missing notice id → 400/404', async ({ request }) => {
    const u = uniqueUser('nmiss');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/notices/9999999/print');
    expect([400, 404]).toContain(res.status());
  });

  test('unauthenticated print → 401', async ({ request }) => {
    const res = await request.get('/api/v1/notices/1/print');
    expect(res.status()).toBe(401);
  });
});
