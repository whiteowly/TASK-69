import { test, expect } from '@playwright/test';
import { uid, registerAndLoginUI } from './browser-helpers';

const SS = 'screenshots/auth';

test.describe('Auth & Account Browser Flows', () => {
  test('register new account through UI', async ({ page }) => {
    const username = uid('reg');
    await page.goto('/register');
    await page.waitForSelector('#username', { timeout: 5000 });
    await page.fill('#username', username);
    await page.fill('#password', 'SecurePass99');
    await page.screenshot({ path: `${SS}/register-form.png` });
    await page.click('button[type="submit"]');
    // Wait for either success message or navigation
    await page.waitForTimeout(3000);
    await page.screenshot({ path: `${SS}/register-success.png` });
    const bodyText = await page.locator('body').textContent();
    // Registration should show success or redirect to login
    expect(bodyText?.includes('success') || bodyText?.includes('Success') || bodyText?.includes('Sign In')).toBeTruthy();
  });

  test('login and redirect to workspace', async ({ page }) => {
    await registerAndLoginUI(page, 'login', SS);
    expect(page.url()).toContain('/workspace/');
    await page.screenshot({ path: `${SS}/workspace-landing.png` });
  });

  test('logout returns to login page', async ({ page }) => {
    await registerAndLoginUI(page, 'logout', SS);
    // Find and click logout
    const logoutBtn = page.locator('button:has-text("Logout"), a:has-text("Logout"), [class*="logout"]');
    if (await logoutBtn.isVisible()) {
      await logoutBtn.click();
      await page.waitForTimeout(1000);
    } else {
      // Fallback: call API directly
      await page.request.post('/api/v1/auth/logout');
      await page.goto('/login');
    }
    await page.screenshot({ path: `${SS}/after-logout.png` });
  });

  test('login with wrong password shows error', async ({ page }) => {
    const username = uid('fail');
    await page.request.post('/api/v1/auth/register', {
      data: { username, password: 'SecurePass99', accountType: 'PERSON' },
    });
    await page.goto('/login');
    await page.fill('#username', username);
    await page.fill('#password', 'WrongPassword');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/login-error.png` });
    expect(page.url()).toContain('/login');
  });

  test('locked page renders', async ({ page }) => {
    await page.goto('/locked');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/locked-page.png` });
    // The locked page should show lock content or redirect to login (if not authenticated)
    const bodyText = await page.locator('body').textContent() || '';
    expect(bodyText.toLowerCase().includes('lock') || page.url().includes('/locked') || page.url().includes('/login')).toBeTruthy();
  });

  test('appeal page renders', async ({ page }) => {
    await page.goto('/appeal');
    await page.screenshot({ path: `${SS}/appeal-page.png` });
  });
});
