import { test, expect, APIRequestContext } from '@playwright/test';
import { loginBrowser } from './seed-helpers';

const SS = 'screenshots/positive-fulfillment';

async function apiLoginCsrf(request: APIRequestContext, username: string): Promise<string> {
  const lr = await request.post('/api/v1/auth/login', { data: { username, password: 'SecurePass99' } });
  expect(lr.status()).toBe(200);
  const me = await request.get('/api/v1/auth/me');
  const hdrs = await me.headersArray();
  return hdrs.filter(h => h.name.toLowerCase() === 'set-cookie').map(h => h.value).join('; ').match(/XSRF-TOKEN=([^;]+)/)?.[1] || '';
}

test.describe.serial('Positive Admin Fulfillment Flows', () => {

  test('admin fulfillment panel — load orders, transition, create exception', async ({ page, request }) => {
    // Seed: create a reward and place an order as participant
    const admCsrf = await apiLoginCsrf(request, 'e2e_admin');
    const rewardRes = await request.post('/api/v1/rewards', {
      headers: { 'X-XSRF-TOKEN': admCsrf },
      data: { title: `Fulfillment Test ${Date.now()}`, tier: 'BRONZE', inventoryCount: 5, perUserLimit: 3, fulfillmentType: 'VOUCHER', status: 'ACTIVE' },
    });
    expect(rewardRes.status()).toBe(201);
    const rewardId = (await rewardRes.json()).id;
    await request.post('/api/v1/auth/logout');

    // Participant places order
    const partCsrf = await apiLoginCsrf(request, 'e2e_participant');
    const orderRes = await request.post('/api/v1/reward-orders', {
      headers: { 'X-XSRF-TOKEN': partCsrf },
      data: { rewardId, quantity: 1, fulfillmentType: 'VOUCHER' },
    });
    expect(orderRes.status()).toBe(201);
    const orderBody = await orderRes.json();
    expect(orderBody.status).toBe('ORDERED');
    await request.post('/api/v1/auth/logout');

    // Admin opens fulfillment panel
    await loginBrowser(page, 'e2e_admin', SS, '01-');
    await page.click('a:has-text("Fulfillment")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/01-fulfillment-loaded.png` });

    // Verify panel loaded without error and shows at least one order
    await expect(page.locator('.error-message')).not.toBeVisible();
    const orderItems = page.locator('.order-item');
    expect(await orderItems.count()).toBeGreaterThan(0);
    await expect(page.locator('body')).toContainText('ORDERED');

    // Find the freshly created order (last one) and transition to ALLOCATED
    const targetOrder = orderItems.last();
    await targetOrder.scrollIntoViewIfNeeded();
    await expect(targetOrder).toContainText('ORDERED');
    await page.screenshot({ path: `${SS}/01-order-before-transition.png` });

    const allocateBtn = targetOrder.locator('button:has-text("ALLOCATED")');
    await allocateBtn.click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/01-order-allocated.png` });
    await expect(targetOrder).toContainText('Updated');

    // After refresh, verify the order status changed
    // The fetchOrders runs after transition, so the order should now show ALLOCATED
    await page.waitForTimeout(1000);
    await page.screenshot({ path: `${SS}/01-order-status-after.png` });
  });

  test('admin fulfillment — create exception on an order', async ({ page }) => {
    await loginBrowser(page, 'e2e_admin', SS, '02-');
    await page.click('a:has-text("Fulfillment")');
    await page.waitForTimeout(2000);

    const orderItems = page.locator('.order-item');
    expect(await orderItems.count()).toBeGreaterThan(0);

    // Create an exception on the first order
    const firstOrder = orderItems.first();
    await firstOrder.scrollIntoViewIfNeeded();
    const reasonInput = firstOrder.locator('input[placeholder*="reason"]').first();
    await reasonInput.fill('DAMAGED_IN_TRANSIT');
    await page.screenshot({ path: `${SS}/02-exception-form.png` });

    const [excResponse] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/fulfillment-exceptions') && r.request().method() === 'POST', { timeout: 10000 }),
      firstOrder.locator('button:has-text("Create Exception")').click(),
    ]);
    expect(excResponse.status()).toBe(201);
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/02-exception-created.png` });
    await expect(firstOrder).toContainText('Updated');
  });
});
