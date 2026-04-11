import { test, expect } from '@playwright/test';
import { loginBrowser } from './seed-helpers';

const SS = 'screenshots/positive-org';

test.describe.serial('Positive Org Operator Flows', () => {

  test('org publishes event — form fill, 201 response, event in list', async ({ page }) => {
    await loginBrowser(page, 'e2e_org', SS, '01-');
    await page.click('a[href*="events"]');
    await page.waitForTimeout(1500);

    await page.fill('#event-org', 'org-e2e');
    await page.fill('#event-title', 'E2E Food Drive');
    await page.selectOption('#event-mode', 'ON_SITE');
    await page.fill('#event-location', 'Community Center');
    await page.fill('#event-start', '2026-07-15T10:00');
    await page.fill('#event-end', '2026-07-15T14:00');
    await page.fill('#event-capacity', '50');
    await page.screenshot({ path: `${SS}/01-event-form.png` });

    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/api/v1/events') && r.request().method() === 'POST', { timeout: 10000 }),
      page.click('button:has-text("Create Event")'),
    ]);
    expect(response.status()).toBe(201);
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/01-event-created.png` });
    await expect(page.locator('body')).toContainText('Event created');
    await expect(page.locator('body')).toContainText('E2E Food Drive');
  });

  test('org publishes resource — form fill, 201 response, resource in list', async ({ page }) => {
    await loginBrowser(page, 'e2e_org', SS, '02-');
    await page.click('a[href*="resources"]');
    await page.waitForTimeout(1500);

    await page.selectOption('#res-type', 'CLAIMABLE_ITEM');
    await page.fill('#res-title', 'E2E Emergency Kit');
    await page.fill('#res-desc', 'Emergency supply kit for families');
    await page.fill('#res-inventory', '25');
    await page.screenshot({ path: `${SS}/02-resource-form.png` });

    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/api/v1/resources') && r.request().method() === 'POST', { timeout: 10000 }),
      page.click('button[type="submit"]'),
    ]);
    expect(response.status()).toBe(201);
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/02-resource-created.png` });
    await expect(page.locator('body')).toContainText('Resource created');
    await expect(page.locator('body')).toContainText('E2E Emergency Kit');
  });
});
