import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser, login } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/registrations/pending          (REVIEW_REGISTRATION)
 *   POST /api/v1/registrations/{id}/decision    (REVIEW_REGISTRATION)
 *   POST /api/v1/registrations/{id}/cancel      (auth, owner-bound)
 */
test.describe('Registrations — pending queue, decisions, cancel', () => {
  test('PARTICIPANT cannot view /registrations/pending (403)', async ({ request }) => {
    const u = uniqueUser('norpend');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/registrations/pending');
    expect(res.status()).toBe(403);
  });

  test('admin can list /registrations/pending → returns array', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/registrations/pending');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('admin approves a manual-review registration; status flips to APPROVED', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Manual Review Event',
        mode: 'ON_SITE',
        startAt: '2026-10-01T10:00:00',
        endAt: '2026-10-01T11:00:00',
        capacity: 5,
        manualReviewRequired: true,
        status: 'PUBLISHED',
      },
    });
    const eventId = (await create.json()).id;

    // Participant registers (will be PENDING_REVIEW).
    const partUser = uniqueUser('rpart');
    const partCsrf = await registerAndLogin(request, partUser);
    const reg = await request.post(`/api/v1/events/${eventId}/registrations`, {
      headers: partCsrf,
      data: { formResponses: '{}' },
    });
    expect(reg.status()).toBe(201);
    const regBody = await reg.json();
    expect(regBody.status).toBe('PENDING_REVIEW');

    // Admin decides.
    const adminCsrf2 = await loginAsSeeded(request, 'admin');
    const dec = await request.post(`/api/v1/registrations/${regBody.id}/decision`, {
      headers: adminCsrf2,
      data: { decision: 'APPROVE', note: 'looks ok' },
    });
    expect(dec.status()).toBe(200);
    const decBody = await dec.json();
    expect(decBody.id).toBe(regBody.id);
    expect(decBody.status).toBe('APPROVED');
  });

  test('participant cancels their own registration → CANCELLED', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Cancel Event',
        mode: 'ON_SITE',
        startAt: '2026-11-01T10:00:00',
        endAt: '2026-11-01T11:00:00',
        capacity: 5,
        status: 'PUBLISHED',
      },
    });
    const eventId = (await create.json()).id;

    const partUser = uniqueUser('rcanc');
    const partCsrf = await registerAndLogin(request, partUser);
    const reg = await request.post(`/api/v1/events/${eventId}/registrations`, {
      headers: partCsrf,
      data: { formResponses: '{}' },
    });
    const regId = (await reg.json()).id;

    const cancel = await request.post(`/api/v1/registrations/${regId}/cancel`, {
      headers: partCsrf,
    });
    expect(cancel.status()).toBe(200);
    const body = await cancel.json();
    expect(body.id).toBe(regId);
    expect(body.status).toBe('CANCELLED');
  });

  test('decision validation — missing decision field → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/registrations/1/decision', {
      headers: adminCsrf,
      data: {},
    });
    expect(res.status()).toBe(400);
  });
});
