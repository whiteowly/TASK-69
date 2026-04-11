import { Page, expect } from '@playwright/test';

const PASSWORD = 'SecurePass99';

/**
 * Login via browser UI. Assumes account already exists with approved role.
 */
export async function loginBrowser(page: Page, username: string, ssDir: string, ssPrefix: string = ''): Promise<void> {
  await page.goto('/login');
  await page.waitForSelector('#username', { timeout: 5000 });
  await page.fill('#username', username);
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL(/workspace/, { timeout: 10000 });
  await page.screenshot({ path: `${ssDir}/${ssPrefix}workspace.png` });
}

/**
 * Register a fresh user via API and return username.
 */
export async function registerViaApi(page: Page, prefix: string): Promise<string> {
  let c = Date.now();
  const username = `${prefix}_${c}`;
  const res = await page.request.post('/api/v1/auth/register', {
    data: { username, password: PASSWORD, accountType: 'PERSON' },
  });
  expect(res.status()).toBe(201);
  return username;
}
