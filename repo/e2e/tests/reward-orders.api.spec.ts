import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/reward-orders                   (MANAGE_REWARD_FULFILLMENT)
 *   POST /api/v1/reward-orders                   (auth)
 *   POST /api/v1/reward-orders/{id}/transition   (MANAGE_REWARD_FULFILLMENT)
 *   POST /api/v1/reward-orders/{id}/tracking     (MANAGE_REWARD_FULFILLMENT)
 *   POST /api/v1/reward-orders/{id}/voucher      (MANAGE_REWARD_FULFILLMENT)
 *   GET  /api/v1/accounts/me/addresses           (auth — required for shipped orders)
 *   POST /api/v1/accounts/me/addresses           (auth)
 */
test.describe('Reward orders — place, list, transition, voucher', () => {
  test('participant places a VOUCHER order; admin lists and issues voucher', async ({ request }) => {
    // Seed a voucher reward as admin.
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const rewardRes = await request.post('/api/v1/rewards', {
      headers: adminCsrf,
      data: {
        title: 'Voucher Reward ' + Date.now(),
        tier: 'SILVER',
        inventoryCount: 5,
        perUserLimit: 2,
        fulfillmentType: 'VOUCHER',
        status: 'ACTIVE',
        organizationId: 'ORG-1',
      },
    });
    expect(rewardRes.status()).toBe(201);
    const rewardId = (await rewardRes.json()).id;

    // Participant places order.
    const partUser = uniqueUser('orderr');
    const partCsrf = await registerAndLogin(request, partUser);
    const orderRes = await request.post('/api/v1/reward-orders', {
      headers: partCsrf,
      data: { rewardId, quantity: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(orderRes.status()).toBe(201);
    const order = await orderRes.json();
    expect(order.id).toBeGreaterThan(0);
    expect(order.rewardId).toBe(rewardId);
    expect(order.quantity).toBe(1);
    expect(order.fulfillmentType).toBe('VOUCHER');

    // Admin lists orders and finds it.
    const adminCsrf2 = await loginAsSeeded(request, 'admin');
    const list = await request.get('/api/v1/reward-orders');
    expect(list.status()).toBe(200);
    const all = await list.json();
    expect(Array.isArray(all)).toBe(true);
    expect(all.some((o: any) => o.id === order.id)).toBe(true);

    // Walk the voucher fulfillment state machine: ORDERED → ALLOCATED → VOUCHER_ISSUED.
    const t1 = await request.post(`/api/v1/reward-orders/${order.id}/transition`, {
      headers: adminCsrf2,
      data: { toState: 'ALLOCATED' },
    });
    expect(t1.status()).toBe(200);
    expect((await t1.json()).status).toBe('ALLOCATED');

    const t2 = await request.post(`/api/v1/reward-orders/${order.id}/transition`, {
      headers: adminCsrf2,
      data: { toState: 'VOUCHER_ISSUED' },
    });
    expect(t2.status()).toBe(200);
    expect((await t2.json()).status).toBe('VOUCHER_ISSUED');

    // Issue the actual voucher code.
    const voucher = await request.post(`/api/v1/reward-orders/${order.id}/voucher`, {
      headers: adminCsrf2,
      data: { voucherCode: 'TEST-VOUCHER-' + Date.now() },
    });
    expect(voucher.status()).toBe(200);
    const voucherBody = await voucher.json();
    expect(voucherBody.id).toBe(order.id);
    expect(voucherBody.voucherCode).toContain('TEST-VOUCHER-');
  });

  test('PARTICIPANT cannot list all reward-orders (403)', async ({ request }) => {
    const u = uniqueUser('nolistro');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/reward-orders');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot transition orders (403)', async ({ request }) => {
    const u = uniqueUser('notrans');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/reward-orders/1/transition', {
      headers: csrf,
      data: { toState: 'FULFILLED' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot set tracking (403)', async ({ request }) => {
    const u = uniqueUser('notrack');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/reward-orders/1/tracking', {
      headers: csrf,
      data: { trackingNumber: 'NO' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot issue voucher (403)', async ({ request }) => {
    const u = uniqueUser('novouc');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/reward-orders/1/voucher', {
      headers: csrf,
      data: { voucherCode: 'X' },
    });
    expect(res.status()).toBe(403);
  });

  test('order placement requires rewardId — missing → 400', async ({ request }) => {
    const u = uniqueUser('badorder');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/reward-orders', {
      headers: csrf,
      data: { quantity: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('Addresses — /accounts/me/addresses', () => {
  test('user creates and lists their own address', async ({ request }) => {
    const u = uniqueUser('addr');
    const csrf = await registerAndLogin(request, u);
    const create = await request.post('/api/v1/accounts/me/addresses', {
      headers: csrf,
      data: { line1: '1 Main St', city: 'Seattle', state: 'WA', zip: '98101' },
    });
    expect(create.status()).toBe(201);
    const addr = await create.json();
    expect(addr.id).toBeGreaterThan(0);
    expect(addr.city).toBe('Seattle');
    expect(addr.stateCode).toBe('WA');
    expect(addr.zipCode).toBe('98101');

    const list = await request.get('/api/v1/accounts/me/addresses');
    expect(list.status()).toBe(200);
    const all = await list.json();
    expect(Array.isArray(all)).toBe(true);
    expect(all.some((a: any) => a.id === addr.id)).toBe(true);

    const setPrimary = await request.put(`/api/v1/accounts/me/addresses/${addr.id}/primary`, {
      headers: csrf,
    });
    expect(setPrimary.status()).toBe(200);
    const updated = await setPrimary.json();
    expect(updated.id).toBe(addr.id);
    expect(updated.primary).toBe(true);
  });

  test('address create validates required fields — missing line1 → 400', async ({ request }) => {
    const u = uniqueUser('badaddr');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/accounts/me/addresses', {
      headers: csrf,
      data: { city: 'Seattle', state: 'WA', zip: '98101' },
    });
    expect(res.status()).toBe(400);
  });
});
