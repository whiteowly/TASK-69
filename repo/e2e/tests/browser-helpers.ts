import { Page, expect } from '@playwright/test';

let counter = 0;
export function uid(prefix = 'e2e'): string {
  return `${prefix}_${Date.now()}_${counter++}`;
}

/**
 * Register a new account via API, then log in through the browser UI.
 * Returns the username. Takes a screenshot after login.
 */
export async function registerAndLoginUI(
  page: Page,
  prefix: string = 'user',
  screenshotDir: string = 'screenshots'
): Promise<string> {
  const username = uid(prefix);
  const password = 'SecurePass99';

  // Register via API (faster than UI)
  const baseURL = page.context()._options?.baseURL || '';
  const regRes = await page.request.post(`${baseURL}/api/v1/auth/register`, {
    data: { username, password, accountType: 'PERSON' },
  });
  expect(regRes.status()).toBe(201);

  // Login via UI
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.screenshot({ path: `${screenshotDir}/${prefix}-login-form.png` });
  await page.click('button[type="submit"]');
  await page.waitForURL(/workspace/, { timeout: 10000 });
  await page.screenshot({ path: `${screenshotDir}/${prefix}-after-login.png` });

  return username;
}

/**
 * Register + login via API only (no browser). Useful for seeding.
 */
export async function apiRegisterLogin(page: Page, prefix: string = 'api'): Promise<string> {
  const username = uid(prefix);
  const password = 'SecurePass99';
  const baseURL = page.context()._options?.baseURL || '';
  await page.request.post(`${baseURL}/api/v1/auth/register`, {
    data: { username, password, accountType: 'PERSON' },
  });
  await page.request.post(`${baseURL}/api/v1/auth/login`, {
    data: { username, password },
  });
  return username;
}
