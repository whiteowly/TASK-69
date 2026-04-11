import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUser, getCsrfHeaders, login, registerAccount } from './helpers';

test.describe('Events and Registrations', () => {
  // Note: These tests go through the real full stack.
  // The default registered user has PARTICIPANT role.
  // We need an ORG_OPERATOR to create events and ADMIN to review.
  // Since the backend uses session-based auth with role from session,
  // and the default registered user gets PARTICIPANT,
  // we test what PARTICIPANT can and cannot do.

  test('PARTICIPANT cannot create event (403)', async ({ request }) => {
    const username = uniqueUser('noev');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/events', {
      headers: csrf,
      data: {
        title: 'Test Event',
        mode: 'ON_SITE',
        startAt: '2026-05-01T17:00:00Z',
        endAt: '2026-05-01T19:00:00Z',
      },
    });
    expect(res.status()).toBe(403);
  });

  test('list events returns 200', async ({ request }) => {
    const username = uniqueUser('listev');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/events');
    expect(res.status()).toBe(200);
  });
});
