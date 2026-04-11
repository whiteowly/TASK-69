import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUser } from './helpers';

test.describe('Alerts and Work Orders', () => {
  test('PARTICIPANT cannot configure alert rules (403)', async ({ request }) => {
    const username = uniqueUser('noalert');
    const csrf = await registerAndLogin(request, username);
    const res = await request.put('/api/v1/alerts/rules/defaults/TEST_TYPE', {
      headers: csrf,
      data: { severity: 'HIGH', thresholdOperator: 'GT', thresholdValue: 100, cooldownSeconds: 60 },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot create work order (403)', async ({ request }) => {
    const username = uniqueUser('nowo');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/work-orders', {
      headers: csrf,
      data: { title: 'Test WO', severity: 'HIGH' },
    });
    expect(res.status()).toBe(403);
  });
});
