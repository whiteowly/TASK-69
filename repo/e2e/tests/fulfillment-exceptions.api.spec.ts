import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   POST /api/v1/fulfillment-exceptions                  (MANAGE_REWARD_FULFILLMENT)
 *   POST /api/v1/fulfillment-exceptions/{id}/transition  (MANAGE_REWARD_FULFILLMENT)
 *   POST /api/v1/fulfillment-exceptions/{id}/reopen      (APPROVE_EXCEPTION_REOPEN — ADMIN only)
 *
 * We seed a reward + order under admin, raise an exception, transition it, and
 * exercise reopen. Negative paths cover authorization and validation.
 */
test.describe('Fulfillment exceptions — full lifecycle', () => {
  test('admin creates exception against an order, transitions it, reopens it', async ({ request }) => {
    // Seed a reward.
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const reward = await request.post('/api/v1/rewards', {
      headers: adminCsrf,
      data: {
        title: 'FE Reward ' + Date.now(),
        tier: 'BRONZE',
        inventoryCount: 5,
        perUserLimit: 1,
        fulfillmentType: 'VOUCHER',
        status: 'ACTIVE',
        organizationId: 'ORG-1',
      },
    });
    const rewardId = (await reward.json()).id;

    // Participant places order.
    const partUser = uniqueUser('feorder');
    const partCsrf = await registerAndLogin(request, partUser);
    const orderRes = await request.post('/api/v1/reward-orders', {
      headers: partCsrf,
      data: { rewardId, quantity: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(orderRes.status()).toBe(201);
    const orderId = (await orderRes.json()).id;

    // Admin creates an exception.
    const adminCsrf2 = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/fulfillment-exceptions', {
      headers: adminCsrf2,
      data: { orderId, reasonCode: 'INVENTORY_LOST', description: 'Stock missing' },
    });
    expect(create.status()).toBe(201);
    const exc = await create.json();
    expect(exc.id).toBeGreaterThan(0);
    expect(exc.orderId).toBe(orderId);
    expect(exc.reasonCode).toBe('INVENTORY_LOST');
    expect(exc.status).toBeTruthy();

    // Walk transitions: OPEN → UNDER_REVIEW → RESOLVED.
    const t1 = await request.post(`/api/v1/fulfillment-exceptions/${exc.id}/transition`, {
      headers: adminCsrf2,
      data: { toState: 'UNDER_REVIEW' },
    });
    expect(t1.status()).toBe(200);
    expect((await t1.json()).status).toBe('UNDER_REVIEW');

    const t2 = await request.post(`/api/v1/fulfillment-exceptions/${exc.id}/transition`, {
      headers: adminCsrf2,
      data: { toState: 'RESOLVED' },
    });
    expect(t2.status()).toBe(200);
    const transBody = await t2.json();
    expect(transBody.id).toBe(exc.id);
    expect(transBody.status).toBe('RESOLVED');

    // Reopen requires APPROVE_EXCEPTION_REOPEN — admin has it.
    const reopen = await request.post(`/api/v1/fulfillment-exceptions/${exc.id}/reopen`, {
      headers: adminCsrf2,
      data: { reasonCode: 'NEW_INFO', note: 'reopening for review' },
    });
    expect(reopen.status()).toBe(200);
    const reopenBody = await reopen.json();
    expect(reopenBody.id).toBe(exc.id);
  });

  test('PARTICIPANT cannot create exception (403)', async ({ request }) => {
    const u = uniqueUser('nofex');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/fulfillment-exceptions', {
      headers: csrf,
      data: { orderId: 1, reasonCode: 'INVENTORY_LOST' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot transition exception (403)', async ({ request }) => {
    const u = uniqueUser('nofext');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/fulfillment-exceptions/1/transition', {
      headers: csrf,
      data: { toState: 'RESOLVED' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot reopen exception (403)', async ({ request }) => {
    const u = uniqueUser('noreop');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/fulfillment-exceptions/1/reopen', {
      headers: csrf,
      data: { reasonCode: 'NO' },
    });
    expect(res.status()).toBe(403);
  });

  test('exception create validates body — missing reasonCode → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/fulfillment-exceptions', {
      headers: adminCsrf,
      data: { orderId: 1 },
    });
    expect(res.status()).toBe(400);
  });

  test('reopen on missing exception id → 400/404', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/fulfillment-exceptions/9999999/reopen', {
      headers: adminCsrf,
      data: { reasonCode: 'X' },
    });
    expect([400, 404]).toContain(res.status());
  });
});
