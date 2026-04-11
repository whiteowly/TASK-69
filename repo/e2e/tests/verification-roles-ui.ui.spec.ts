import { test, expect } from '@playwright/test';
import { registerAndLoginUI } from './browser-helpers';

const SS = 'screenshots/verification';

test.describe('Verification & Roles Browser Flows', () => {
  test('person verification submission form via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'pv', SS);
    // Navigate via SPA sidebar
    await page.click('a[href*="verification"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/verification-page.png` });

    // Fill verification form
    const nameInput = page.locator('input[id*="legal"], input[id*="name"], input[placeholder*="name" i]').first();
    const dobInput = page.locator('input[type="date"], input[id*="dob"], input[id*="birth"]').first();
    if (await nameInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await nameInput.fill('Jane E2E Tester');
      if (await dobInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await dobInput.fill('1990-05-15');
      }
      await page.screenshot({ path: `${SS}/verification-filled.png` });
      await page.click('button[type="submit"]');
      await page.waitForTimeout(2000);
      await page.screenshot({ path: `${SS}/verification-submitted.png` });
    }
  });

  test('role management page via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'role', SS);
    await page.click('a[href*="roles"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/role-management.png` });
  });
});
