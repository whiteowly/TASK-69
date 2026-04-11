import { test, expect } from '@playwright/test';
import { registerAndLoginUI } from './browser-helpers';

const SS = 'screenshots/events';

test.describe('Events, Resources & Rewards Browser Flows', () => {
  test('participant event browse via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'evbrowse', SS);
    await page.click('a[href*="events"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/event-browse.png` });
    await expect(page.locator('body')).toBeVisible();
  });

  test('participant resource browse via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'resbrowse', SS);
    await page.click('a[href*="resources"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/resource-browse.png` });
  });

  test('participant reward catalog via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'rewbrowse', SS);
    await page.click('a[href*="rewards"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/reward-catalog.png` });
  });
});
