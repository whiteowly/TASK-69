import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUser } from './helpers';

test.describe('Analytics, Reporting, and Audit', () => {
  test('PARTICIPANT cannot access audit logs (403)', async ({ request }) => {
    const username = uniqueUser('noaudit');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/admin/audit-logs');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot execute reports (403)', async ({ request }) => {
    const username = uniqueUser('norpt');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/reports/templates/999/execute', {
      headers: csrf,
      data: { format: 'CSV' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot access operations summary (403)', async ({ request }) => {
    const username = uniqueUser('nosum');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/analytics/operations-summary?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59');
    expect(res.status()).toBe(403);
  });
});
