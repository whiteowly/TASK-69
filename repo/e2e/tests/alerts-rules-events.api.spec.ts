import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/alerts/rules                                          (CONFIGURE_ALERT_RULES)
 *   PUT  /api/v1/alerts/rules/defaults/{alertType}                     (CONFIGURE_ALERT_RULES)
 *   PUT  /api/v1/alerts/rules/overrides/{scopeType}/{scopeId}/{alertType} (CONFIGURE_ALERT_RULES)
 *   POST /api/v1/alerts/events                                         (CONFIGURE_ALERT_RULES)
 */
test.describe('Alert rules — read, defaults, overrides', () => {
  test('admin can read /alerts/rules → returns map with arrays for defaults+overrides', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/alerts/rules');
    expect(res.status()).toBe(200);
    const body = await res.json();
    // Response is Map<String, List<?>>; just assert object-shaped.
    expect(typeof body).toBe('object');
    // Most implementations expose 'defaults' / 'overrides' keys.
    const keys = Object.keys(body);
    expect(keys.length).toBeGreaterThan(0);
    for (const k of keys) {
      expect(Array.isArray(body[k])).toBe(true);
    }
  });

  test('admin upserts a default alert rule and reads back the new severity/threshold', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const alertType = 'TEST_ALERT_' + Date.now();
    const upsert = await request.put(`/api/v1/alerts/rules/defaults/${alertType}`, {
      headers: adminCsrf,
      data: {
        severity: 'HIGH',
        thresholdOperator: 'GT',
        thresholdValue: 100,
        thresholdUnit: 'count',
        durationSeconds: 0,
        cooldownSeconds: 60,
      },
    });
    expect(upsert.status()).toBe(200);
    const body = await upsert.json();
    expect(body.id).toBeGreaterThan(0);
    expect(body.alertType).toBe(alertType);
    expect(body.severity).toBe('HIGH');
    expect(body.thresholdOperator).toBe('GT');
    expect(body.thresholdValue).toBe(100);
  });

  test('admin upserts an override rule', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const alertType = 'OVR_ALERT_' + Date.now();
    const upsert = await request.put(`/api/v1/alerts/rules/overrides/ORG/ORG-1/${alertType}`, {
      headers: adminCsrf,
      data: {
        severity: 'MEDIUM',
        thresholdOperator: 'GTE',
        thresholdValue: 50,
        thresholdUnit: 'count',
        cooldownSeconds: 30,
      },
    });
    expect(upsert.status()).toBe(200);
    const body = await upsert.json();
    expect(body.alertType).toBe(alertType);
    expect(body.scopeType).toBe('ORG');
    expect(body.scopeId).toBe('ORG-1');
    expect(body.severity).toBe('MEDIUM');
    expect(body.thresholdValue).toBe(50);
  });

  test('PARTICIPANT cannot read /alerts/rules (403)', async ({ request }) => {
    const u = uniqueUser('norules');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/alerts/rules');
    expect(res.status()).toBe(403);
  });

  test('alert default upsert validates body — missing severity → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.put('/api/v1/alerts/rules/defaults/SOMETHING', {
      headers: adminCsrf,
      data: { thresholdOperator: 'GT', thresholdValue: 1 },
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('Alert events — POST /alerts/events', () => {
  test('admin can ingest an alert event', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    // Define a default rule so the event evaluates against a known threshold.
    const alertType = 'INGEST_ALERT_' + Date.now();
    await request.put(`/api/v1/alerts/rules/defaults/${alertType}`, {
      headers: adminCsrf,
      data: {
        severity: 'HIGH',
        thresholdOperator: 'GT',
        thresholdValue: 10,
        cooldownSeconds: 60,
      },
    });

    // Use a measuredValue *below* the threshold so we don't trigger auto-creation
    // of a system-owned WorkOrder (which has its own org-bound flow).
    const res = await request.post('/api/v1/alerts/events', {
      headers: adminCsrf,
      data: { alertType, scopeType: 'ORG', scopeId: 'ORG-1', measuredValue: 5, unit: 'count' },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.id).toBeGreaterThan(0);
    expect(body.alertType).toBe(alertType);
    expect(body.measuredValue).toBe(5);
    // No work order should be auto-created when the threshold isn't exceeded.
    expect(body.workOrderId).toBeFalsy();
  });

  test('PARTICIPANT cannot ingest alert event (403)', async ({ request }) => {
    const u = uniqueUser('noing');
    const csrf = await registerAndLogin(request, u);
    const res = await request.post('/api/v1/alerts/events', {
      headers: csrf,
      data: { alertType: 'X', measuredValue: 1 },
    });
    expect(res.status()).toBe(403);
  });

  test('alert event missing alertType → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/alerts/events', {
      headers: adminCsrf,
      data: { measuredValue: 1 },
    });
    expect(res.status()).toBe(400);
  });
});
