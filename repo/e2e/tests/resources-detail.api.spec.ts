import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   POST /api/v1/resources                    (PUBLISH_RESOURCE)
 *   GET  /api/v1/resources                    (auth)
 *   GET  /api/v1/resources/{id}               (auth)
 *   POST /api/v1/resources/{id}/claim         (auth)
 *   POST /api/v1/resources/files/{id}/download (auth)
 *
 * Organization-bound CLAIMABLE_ITEM resources are created by ADMIN (which
 * bypasses org-scope checks) and read back by a fresh participant.
 */
test.describe('Resources — publish, read by id, claim', () => {
  test('admin publishes claimable resource; participant reads /resources/{id}', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/resources', {
      headers: adminCsrf,
      data: {
        type: 'CLAIMABLE_ITEM',
        title: 'Detail Resource ' + Date.now(),
        description: 'A claimable item for testing',
        inventoryCount: 5,
        organizationId: 'ORG-1',
        status: 'PUBLISHED',
      },
    });
    expect(create.status()).toBe(201);
    const r = await create.json();
    expect(r.id).toBeGreaterThan(0);
    expect(r.type).toBe('CLAIMABLE_ITEM');
    expect(r.title).toContain('Detail Resource');
    expect(r.inventoryCount).toBe(5);

    // Read as fresh participant.
    const partUser = uniqueUser('rdetail');
    await registerAndLogin(request, partUser);
    const get = await request.get(`/api/v1/resources/${r.id}`);
    expect(get.status()).toBe(200);
    const fetched = await get.json();
    expect(fetched.id).toBe(r.id);
    expect(fetched.title).toBe(r.title);
    expect(fetched.status).toBe(r.status);
  });

  test('GET /resources/{id} on missing id returns 400/404', async ({ request }) => {
    const u = uniqueUser('rmiss');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/resources/9999999');
    expect([400, 404]).toContain(res.status());
  });

  test('participant claims a CLAIMABLE_ITEM resource → ClaimResponse contains result', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/resources', {
      headers: adminCsrf,
      data: {
        type: 'CLAIMABLE_ITEM',
        title: 'Claim Test ' + Date.now(),
        inventoryCount: 3,
        organizationId: 'ORG-1',
        status: 'PUBLISHED',
      },
    });
    const resourceId = (await create.json()).id;

    // Participant claims.
    const partUser = uniqueUser('rclaim');
    const partCsrf = await registerAndLogin(request, partUser);
    const claim = await request.post(`/api/v1/resources/${resourceId}/claim`, {
      headers: partCsrf,
    });
    expect(claim.status()).toBe(200);
    const body = await claim.json();
    expect(body.claimId).toBeGreaterThan(0);
    expect(body.result).toBeTruthy(); // GRANTED, INSUFFICIENT_INVENTORY, etc.
  });

  test('PARTICIPANT cannot publish resource (403)', async ({ request }) => {
    const u = uniqueUser('nopub');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/resources', {
      headers: csrf,
      data: { type: 'CLAIMABLE_ITEM', title: 'NO', inventoryCount: 1, organizationId: 'ORG-1' },
    });
    expect(res.status()).toBe(403);
  });

  test('publish validation: missing title → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/resources', {
      headers: adminCsrf,
      data: { type: 'CLAIMABLE_ITEM', inventoryCount: 1, organizationId: 'ORG-1' },
    });
    expect(res.status()).toBe(400);
  });
});
