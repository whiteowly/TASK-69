import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUser, getCsrfHeaders, login, registerAccount } from './helpers';

test.describe('Verification and Role Lifecycle', () => {
  test('submit person verification', async ({ request }) => {
    const username = uniqueUser('pv');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/verification/person', {
      headers: csrf,
      data: { legalName: 'Jane Test', dateOfBirth: '1990-05-15' },
    });
    expect(res.status()).toBe(202);
    const body = await res.json();
    expect(body.status).toBe('UNDER_REVIEW');
  });

  test('request role as PARTICIPANT', async ({ request }) => {
    const username = uniqueUser('rr');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/accounts/me/role-requests', {
      headers: csrf,
      data: { role: 'PARTICIPANT' },
    });
    // Should succeed or return role-specific response
    expect([200, 201, 202].includes(res.status())).toBeTruthy();
  });

  test('PARTICIPANT cannot access admin verification queue', async ({ request }) => {
    const username = uniqueUser('noadmin');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/admin/verification/queue');
    expect(res.status()).toBe(403);
  });
});
