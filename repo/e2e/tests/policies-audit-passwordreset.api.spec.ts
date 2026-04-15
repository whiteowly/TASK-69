import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser, registerAccount } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/resource-policies                  (MANAGE_RESOURCE_POLICY)
 *   POST /api/v1/resource-policies                  (MANAGE_RESOURCE_POLICY)
 *   GET  /api/v1/admin/audit-logs                   (VIEW_AUDIT_LOGS)
 *   POST /api/v1/admin/password-resets              (RESET_PASSWORD)
 */
test.describe('Resource policies — list and create', () => {
  test('admin can create then list a usage policy', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/resource-policies', {
      headers: adminCsrf,
      data: {
        name: 'policy_' + Date.now(),
        scope: 'PER_USER',
        maxActions: 3,
        windowDays: 30,
        resourceAction: 'CLAIM',
      },
    });
    expect(create.status()).toBe(201);
    const p = await create.json();
    expect(p.id).toBeGreaterThan(0);
    expect(p.scope).toBe('PER_USER');
    expect(p.maxActions).toBe(3);
    expect(p.windowDays).toBe(30);

    const list = await request.get('/api/v1/resource-policies');
    expect(list.status()).toBe(200);
    const arr = await list.json();
    expect(Array.isArray(arr)).toBe(true);
    expect(arr.some((x: any) => x.id === p.id)).toBe(true);
  });

  test('PARTICIPANT cannot list policies (403)', async ({ request }) => {
    const u = uniqueUser('nopol');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/resource-policies');
    expect(res.status()).toBe(403);
  });

  test('policy create validates body — missing name → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/resource-policies', {
      headers: adminCsrf,
      data: { scope: 'PER_USER', maxActions: 1, windowDays: 1, resourceAction: 'CLAIM' },
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('Audit logs — admin view', () => {
  test('admin can read /admin/audit-logs and gets a paged response', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/admin/audit-logs');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('items');
    expect(body).toHaveProperty('page');
    expect(body).toHaveProperty('size');
    expect(body).toHaveProperty('total');
    expect(Array.isArray(body.items)).toBe(true);
  });

  test('admin can filter audit logs by actionType', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/admin/audit-logs?actionType=ACCOUNT_REGISTERED&size=5');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.size).toBe(5);
    expect(Array.isArray(body.items)).toBe(true);
    // Every returned record (if any) should match the filter.
    for (const log of body.items) {
      expect(log.actionType).toBe('ACCOUNT_REGISTERED');
    }
  });
});

test.describe('Admin password resets', () => {
  test('admin can request a password reset for an existing target', async ({ request }) => {
    // Create target.
    const targetName = uniqueUser('pwdtarget');
    const reg = await registerAccount(request, targetName);
    const targetId = (await reg.json()).accountId;

    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/admin/password-resets', {
      headers: adminCsrf,
      data: { targetAccountId: targetId, identityReviewNote: 'ID verified in person' },
    });
    expect(res.status()).toBe(202);
    const body = await res.json();
    // PasswordResetResponse contains at least the temp token + expiry.
    expect(body).toBeTruthy();
  });

  test('PARTICIPANT cannot trigger password reset (403)', async ({ request }) => {
    const u = uniqueUser('nopwd');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/admin/password-resets', {
      headers: csrf,
      data: { targetAccountId: 1, identityReviewNote: 'no' },
    });
    expect(res.status()).toBe(403);
  });

  test('password reset validates body — missing identityReviewNote → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/admin/password-resets', {
      headers: adminCsrf,
      data: { targetAccountId: 1 },
    });
    expect(res.status()).toBe(400);
  });
});
