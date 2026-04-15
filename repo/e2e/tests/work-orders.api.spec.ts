import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   POST /api/v1/work-orders                              (CONFIGURE_ALERT_RULES)
 *   GET  /api/v1/work-orders                              (CONFIGURE_ALERT_RULES)
 *   GET  /api/v1/work-orders/{id}                         (CONFIGURE_ALERT_RULES)
 *   POST /api/v1/work-orders/{id}/transition              (CONFIGURE_ALERT_RULES)
 *   POST /api/v1/work-orders/{id}/assign                  (CONFIGURE_ALERT_RULES)
 *   POST /api/v1/work-orders/{id}/notes                   (CONFIGURE_ALERT_RULES)
 *   POST /api/v1/work-orders/{id}/post-incident-review    (CONFIGURE_ALERT_RULES)
 *   (POST /work-orders/{id}/photos exercised separately — multipart upload.)
 */
test.describe('Work orders — full incident lifecycle', () => {
  test('admin creates work order, lists it, gets detail, transitions, adds note, post-incident-review', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    // Create.
    const create = await request.post('/api/v1/work-orders', {
      headers: adminCsrf,
      data: {
        title: 'Generator failure ' + Date.now(),
        description: 'Power loss in zone A',
        severity: 'HIGH',
        organizationId: 'ORG-1',
      },
    });
    expect(create.status()).toBe(201);
    const wo = await create.json();
    expect(wo.id).toBeGreaterThan(0);
    expect(wo.title).toContain('Generator failure');
    expect(wo.severity).toBe('HIGH');
    expect(wo.status).toBeTruthy();
    expect(wo.createdBy).toBeGreaterThan(0);

    // List — admin sees all.
    const list = await request.get('/api/v1/work-orders');
    expect(list.status()).toBe(200);
    const all = await list.json();
    expect(Array.isArray(all)).toBe(true);
    expect(all.some((x: any) => x.id === wo.id)).toBe(true);

    // List filter by status.
    const filtered = await request.get(`/api/v1/work-orders?status=${encodeURIComponent(wo.status)}`);
    expect(filtered.status()).toBe(200);

    // Detail.
    const detail = await request.get(`/api/v1/work-orders/${wo.id}`);
    expect(detail.status()).toBe(200);
    const detailBody = await detail.json();
    expect(detailBody.id).toBe(wo.id);
    expect(detailBody.title).toBe(wo.title);

    // Walk the WO state machine: NEW_ALERT → ACKNOWLEDGED → DISPATCHED → IN_PROGRESS.
    for (const next of ['ACKNOWLEDGED', 'DISPATCHED', 'IN_PROGRESS']) {
      const trans = await request.post(`/api/v1/work-orders/${wo.id}/transition`, {
        headers: adminCsrf,
        data: { toStatus: next },
      });
      expect(trans.status()).toBe(200);
      expect((await trans.json()).status).toBe(next);
    }

    // Add note.
    const note = await request.post(`/api/v1/work-orders/${wo.id}/notes`, {
      headers: adminCsrf,
      data: { content: 'On-site team dispatched' },
    });
    expect(note.status()).toBe(201);
    const noteBody = await note.json();
    expect(noteBody.id).toBeGreaterThan(0);
    expect(noteBody.workOrderId).toBe(wo.id);
    expect(noteBody.content).toBe('On-site team dispatched');

    // Resolve then post-incident-review (IN_PROGRESS → RESOLVED).
    const resolve = await request.post(`/api/v1/work-orders/${wo.id}/transition`, {
      headers: adminCsrf,
      data: { toStatus: 'RESOLVED' },
    });
    expect(resolve.status()).toBe(200);

    const pir = await request.post(`/api/v1/work-orders/${wo.id}/post-incident-review`, {
      headers: adminCsrf,
      data: {
        summary: 'Generator restored within SLA',
        lessons: 'Need redundant generator',
        actions: 'Procure backup unit',
      },
    });
    expect(pir.status()).toBe(201);
    const pirBody = await pir.json();
    expect(pirBody.workOrderId).toBe(wo.id);
    expect(pirBody.summary).toBe('Generator restored within SLA');
  });

  test('admin can assign a work order to another account once it is DISPATCHED', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const create = await request.post('/api/v1/work-orders', {
      headers: adminCsrf,
      data: {
        title: 'Assign Test',
        severity: 'MEDIUM',
        organizationId: 'ORG-1',
      },
    });
    const woId = (await create.json()).id;

    // Walk into DISPATCHED — assignment is only allowed from that status.
    for (const next of ['ACKNOWLEDGED', 'DISPATCHED']) {
      const t = await request.post(`/api/v1/work-orders/${woId}/transition`, {
        headers: adminCsrf,
        data: { toStatus: next },
      });
      expect(t.status()).toBe(200);
    }

    // Assign to admin (id=1 from seed).
    const assign = await request.post(`/api/v1/work-orders/${woId}/assign`, {
      headers: adminCsrf,
      data: { assigneeId: 1 },
    });
    expect(assign.status()).toBe(200);
    const body = await assign.json();
    expect(body.id).toBe(woId);
    expect(body.assignedTo).toBe(1);
  });

  test('PARTICIPANT cannot list work orders (403)', async ({ request }) => {
    const u = uniqueUser('nowo2');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/work-orders');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot get work order detail (403)', async ({ request }) => {
    const u = uniqueUser('nowodet');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/work-orders/1');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot transition work order (403)', async ({ request }) => {
    const u = uniqueUser('nowot');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/work-orders/1/transition', {
      headers: csrf,
      data: { toStatus: 'CLOSED' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot add note (403)', async ({ request }) => {
    const u = uniqueUser('nowon');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/work-orders/1/notes', {
      headers: csrf,
      data: { content: 'x' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot create post-incident-review (403)', async ({ request }) => {
    const u = uniqueUser('nopir');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/work-orders/1/post-incident-review', {
      headers: csrf,
      data: { summary: 'x' },
    });
    expect(res.status()).toBe(403);
  });

  test('work order create validates body — missing title → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/work-orders', {
      headers: adminCsrf,
      data: { severity: 'HIGH', organizationId: 'ORG-1' },
    });
    expect(res.status()).toBe(400);
  });

  test('work order detail unknown id → 400/404', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/work-orders/9999999');
    expect([400, 404]).toContain(res.status());
  });
});
