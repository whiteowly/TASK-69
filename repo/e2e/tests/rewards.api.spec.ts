import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   POST /api/v1/rewards            (MANAGE_REWARDS)
 *   GET  /api/v1/rewards            (auth)
 *   GET  /api/v1/rewards/{id}       (auth)
 *
 * Negative paths cover unauthenticated access and validation. Positive paths
 * verify both list shape and detail shape match the response DTOs.
 */
test.describe('Rewards — create, list, get', () => {
  test('admin creates reward; participant lists and reads /rewards/{id}', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const title = 'Test Reward ' + Date.now();
    const create = await request.post('/api/v1/rewards', {
      headers: adminCsrf,
      data: {
        title,
        description: 'A test reward',
        tier: 'BRONZE',
        inventoryCount: 20,
        perUserLimit: 1,
        fulfillmentType: 'VOUCHER',
        status: 'ACTIVE',
        organizationId: 'ORG-1',
      },
    });
    expect(create.status()).toBe(201);
    const r = await create.json();
    expect(r.id).toBeGreaterThan(0);
    expect(r.title).toBe(title);
    expect(r.tier).toBe('BRONZE');
    expect(r.inventoryCount).toBe(20);
    expect(r.fulfillmentType).toBe('VOUCHER');
    expect(r.status).toBe('ACTIVE');

    // List.
    const partUser = uniqueUser('rwlist');
    await registerAndLogin(request, partUser);
    const list = await request.get('/api/v1/rewards');
    expect(list.status()).toBe(200);
    const arr = await list.json();
    expect(Array.isArray(arr)).toBe(true);
    expect(arr.some((x: any) => x.id === r.id && x.title === title)).toBe(true);

    // Detail.
    const get = await request.get(`/api/v1/rewards/${r.id}`);
    expect(get.status()).toBe(200);
    const fetched = await get.json();
    expect(fetched.id).toBe(r.id);
    expect(fetched.title).toBe(title);
    expect(fetched.perUserLimit).toBe(1);
  });

  test('GET /rewards is reachable for any authenticated user', async ({ request }) => {
    const u = uniqueUser('rwlist2');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/rewards');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('GET /rewards/{id} unknown → 400/404', async ({ request }) => {
    const u = uniqueUser('rwmiss');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/rewards/9999999');
    expect([400, 404]).toContain(res.status());
  });

  test('PARTICIPANT cannot create reward (403)', async ({ request }) => {
    const u = uniqueUser('rwnocreate');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/rewards', {
      headers: csrf,
      data: { title: 'NO', inventoryCount: 1, perUserLimit: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(res.status()).toBe(403);
  });

  test('reward create validates body — missing title → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/rewards', {
      headers: adminCsrf,
      data: { inventoryCount: 1, perUserLimit: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(res.status()).toBe(400);
  });

  test('unauthenticated /rewards → 401', async ({ request }) => {
    const res = await request.get('/api/v1/rewards');
    expect(res.status()).toBe(401);
  });
});
