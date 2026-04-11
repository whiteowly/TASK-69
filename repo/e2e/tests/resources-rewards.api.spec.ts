import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUser } from './helpers';

test.describe('Resources and Rewards', () => {
  test('list resources returns 200', async ({ request }) => {
    const username = uniqueUser('lres');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/resources');
    expect(res.status()).toBe(200);
  });

  test('PARTICIPANT cannot publish resource (403)', async ({ request }) => {
    const username = uniqueUser('nores');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/resources', {
      headers: csrf,
      data: { type: 'CLAIMABLE_ITEM', title: 'Test', inventoryCount: 10 },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot create reward (403)', async ({ request }) => {
    const username = uniqueUser('norew');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/rewards', {
      headers: csrf,
      data: { title: 'Test Reward', inventoryCount: 10, perUserLimit: 1, fulfillmentType: 'PHYSICAL_SHIPMENT' },
    });
    expect(res.status()).toBe(403);
  });
});
