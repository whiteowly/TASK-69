import { test, expect } from '@playwright/test';
import { registerAccount, login, getCsrfHeaders, registerAndLogin, uniqueUser } from './helpers';

test.describe('Auth and Account Lifecycle', () => {
  test('register creates account and returns 201', async ({ request }) => {
    const username = uniqueUser('reg');
    const res = await registerAccount(request, username);
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.username).toBe(username);
    expect(body.status).toBe('ACTIVE');
  });

  test('login succeeds with valid credentials', async ({ request }) => {
    const username = uniqueUser('login');
    await registerAccount(request, username);
    const res = await login(request, username);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.username).toBe(username);
  });

  test('login fails with wrong password returns 401', async ({ request }) => {
    const username = uniqueUser('badfail');
    await registerAccount(request, username);
    const res = await login(request, username, 'WrongPassword!');
    expect(res.status()).toBe(401);
  });

  test('lockout after 10 failed attempts returns 423', async ({ request }) => {
    const username = uniqueUser('lockout');
    await registerAccount(request, username);
    for (let i = 0; i < 10; i++) {
      await login(request, username, 'wrong');
    }
    const res = await login(request, username, 'wrong');
    expect(res.status()).toBe(423);
  });

  test('logout invalidates session', async ({ request }) => {
    const username = uniqueUser('logout');
    await registerAndLogin(request, username);
    // Verify we're logged in
    const preLogout = await request.get('/api/v1/events');
    expect(preLogout.status()).toBe(200);
    // Logout (CSRF-exempt endpoint)
    const logoutRes = await request.post('/api/v1/auth/logout');
    expect([200, 204].includes(logoutRes.status())).toBeTruthy();
    // Subsequent /me should fail
    const meRes = await request.get('/api/v1/auth/me');
    expect(meRes.status()).toBe(401);
  });

  test('unauthenticated request returns 401', async ({ request }) => {
    const res = await request.get('/api/v1/events');
    expect(res.status()).toBe(401);
  });

  test('duplicate username returns 409', async ({ request }) => {
    const username = uniqueUser('dup');
    await registerAccount(request, username);
    const res = await registerAccount(request, username);
    expect(res.status()).toBe(409);
  });
});
