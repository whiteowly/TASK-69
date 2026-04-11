import { test, expect } from '@playwright/test';
import { registerAndLoginUI } from './browser-helpers';

const SS = 'screenshots/participant';

test.describe('Participant Workspace Full Browser Flows', () => {
  test('workspace shell shows navigation with all links', async ({ page }) => {
    await registerAndLoginUI(page, 'nav', SS);
    await page.screenshot({ path: `${SS}/workspace-shell.png`, fullPage: true });
    const bodyText = await page.locator('body').textContent();
    // Verify sidebar nav items exist
    expect(bodyText).toContain('Verification');
    expect(bodyText).toContain('Events');
    expect(bodyText).toContain('Resources');
    expect(bodyText).toContain('Rewards');
  });

  test('verification submit — navigate via sidebar and fill form', async ({ page }) => {
    await registerAndLoginUI(page, 'idvfy', SS);
    // Navigate via SPA sidebar link
    await page.click('a[href*="verification"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/id-verification-page.png` });

    // Try to fill the verification form
    const nameField = page.locator('#legalName, input[placeholder*="legal" i], input[placeholder*="name" i]').first();
    if (await nameField.isVisible({ timeout: 3000 }).catch(() => false)) {
      await nameField.fill('Alice E2E Participant');
      const dobField = page.locator('#dateOfBirth, input[type="date"]').first();
      if (await dobField.isVisible({ timeout: 2000 }).catch(() => false)) {
        await dobField.fill('1988-03-22');
      }
      await page.screenshot({ path: `${SS}/id-verification-filled.png` });
      const submitBtn = page.locator('button[type="submit"]').first();
      if (await submitBtn.isVisible()) {
        await submitBtn.click();
        await page.waitForTimeout(2000);
        await page.screenshot({ path: `${SS}/id-verification-result.png` });
      }
    }
  });

  test('events page — browse via sidebar navigation', async ({ page }) => {
    await registerAndLoginUI(page, 'evlist', SS);
    await page.click('a[href*="events"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/events-list.png` });
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('resources page — browse via sidebar navigation', async ({ page }) => {
    await registerAndLoginUI(page, 'reslist', SS);
    await page.click('a[href*="resources"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/resources-list.png` });
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('rewards page — catalog via sidebar navigation', async ({ page }) => {
    await registerAndLoginUI(page, 'rewcat', SS);
    await page.click('a[href*="rewards"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/rewards-catalog.png` });
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('roles page — navigate via sidebar', async ({ page }) => {
    await registerAndLoginUI(page, 'rolereq', SS);
    await page.click('a[href*="roles"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/roles-page.png` });
  });
});
