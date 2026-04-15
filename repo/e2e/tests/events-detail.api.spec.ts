import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   POST   /api/v1/events                              (PUBLISH_EVENT)
 *   GET    /api/v1/events                              (auth)
 *   GET    /api/v1/events/{id}                         (auth)
 *   PATCH  /api/v1/events/{id}                         (PUBLISH_EVENT)
 *   POST   /api/v1/events/{id}/registrations           (auth)
 *   GET    /api/v1/events/{id}/roster                  (REVIEW_REGISTRATION)
 *   POST   /api/v1/events/{id}/roster/export           (EXPORT_REPORTS)
 */
test.describe('Events — create, read, patch, register, roster, export', () => {
  test('admin creates event, anyone authenticated reads it back via /events/{id}', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Detail Event ' + Date.now(),
        description: 'Tested event',
        mode: 'ON_SITE',
        location: 'HQ',
        startAt: '2026-06-01T17:00:00',
        endAt: '2026-06-01T19:00:00',
        capacity: 25,
        waitlistEnabled: true,
        manualReviewRequired: false,
        status: 'PUBLISHED',
      },
    });
    expect(create.status()).toBe(201);
    const event = await create.json();
    expect(event.id).toBeGreaterThan(0);
    expect(event.title).toContain('Detail Event');
    expect(event.status).toBe('PUBLISHED');
    expect(event.capacity).toBe(25);

    // GET /events/{id} as a participant — anyone authenticated.
    const participantUser = uniqueUser('evview');
    await registerAndLogin(request, participantUser);
    const getRes = await request.get(`/api/v1/events/${event.id}`);
    expect(getRes.status()).toBe(200);
    const fetched = await getRes.json();
    expect(fetched.id).toBe(event.id);
    expect(fetched.title).toBe(event.title);
    expect(fetched.organizationId).toBe('ORG-1');
    expect(fetched.location).toBe('HQ');
  });

  test('GET /events/{id} on missing id returns 400/404', async ({ request }) => {
    const user = uniqueUser('evmiss');
    await registerAndLogin(request, user);
    const res = await request.get('/api/v1/events/9999999');
    expect([400, 404]).toContain(res.status());
  });

  test('PATCH /events/{id} updates fields and returns updated event', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Patch Me',
        mode: 'ONLINE',
        startAt: '2026-07-01T10:00:00',
        endAt: '2026-07-01T11:00:00',
        capacity: 5,
        status: 'DRAFT',
      },
    });
    const eventId = (await create.json()).id;

    const patch = await request.patch(`/api/v1/events/${eventId}`, {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Patched Title',
        mode: 'ONLINE',
        startAt: '2026-07-01T10:00:00',
        endAt: '2026-07-01T12:00:00',
        capacity: 10,
        status: 'PUBLISHED',
      },
    });
    expect(patch.status()).toBe(200);
    const patched = await patch.json();
    expect(patched.id).toBe(eventId);
    expect(patched.title).toBe('Patched Title');
    expect(patched.capacity).toBe(10);
    expect(patched.status).toBe('PUBLISHED');
  });

  test('participant registers for event then admin sees them on the roster', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Roster Event',
        mode: 'ON_SITE',
        location: 'HQ',
        startAt: '2026-08-01T10:00:00',
        endAt: '2026-08-01T11:00:00',
        capacity: 10,
        status: 'PUBLISHED',
      },
    });
    const eventId = (await create.json()).id;

    // Participant registers.
    const partUser = uniqueUser('evpart');
    const partCsrf = await registerAndLogin(request, partUser);
    const reg = await request.post(`/api/v1/events/${eventId}/registrations`, {
      headers: partCsrf,
      data: { formResponses: '{}' },
    });
    expect(reg.status()).toBe(201);
    const regBody = await reg.json();
    expect(regBody.eventId).toBe(eventId);
    expect(['PENDING_REVIEW', 'APPROVED', 'WAITLISTED']).toContain(regBody.status);

    // Admin views the roster.
    await loginAsSeeded(request, 'admin');
    const roster = await request.get(`/api/v1/events/${eventId}/roster`);
    expect(roster.status()).toBe(200);
    const list = await roster.json();
    expect(Array.isArray(list)).toBe(true);
    expect(list.length).toBeGreaterThan(0);
    expect(list.some((r: any) => r.registrationId === regBody.id || r.id === regBody.id)).toBe(true);
  });

  test('PARTICIPANT cannot view roster (403)', async ({ request }) => {
    const user = uniqueUser('norost');
    await registerAndLogin(request, user);
    const res = await request.get('/api/v1/events/1/roster');
    expect(res.status()).toBe(403);
  });

  test('admin can export roster — returns exportFilePath + format', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        title: 'Export Event',
        mode: 'ON_SITE',
        startAt: '2026-09-01T10:00:00',
        endAt: '2026-09-01T11:00:00',
        capacity: 1,
        status: 'PUBLISHED',
      },
    });
    const eventId = (await create.json()).id;

    const exp = await request.post(`/api/v1/events/${eventId}/roster/export?format=CSV`, {
      headers: adminCsrf,
      data: {},
    });
    expect(exp.status()).toBe(200);
    const body = await exp.json();
    expect(body.format).toBe('CSV');
    expect(body.exportFilePath).toBeTruthy();
  });

  test('event create requires title — 400 on validation failure', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/events', {
      headers: adminCsrf,
      data: {
        organizationId: 'ORG-1',
        mode: 'ON_SITE',
        startAt: '2026-09-01T10:00:00',
        endAt: '2026-09-01T11:00:00',
      },
    });
    expect(res.status()).toBe(400);
  });
});
