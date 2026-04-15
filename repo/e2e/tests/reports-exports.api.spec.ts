import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/reports/metric-definitions               (MANAGE_METRICS)
 *   POST /api/v1/reports/metric-definitions               (MANAGE_METRICS)
 *   GET  /api/v1/reports/templates                        (MANAGE_REPORT_TEMPLATES)
 *   POST /api/v1/reports/templates                        (MANAGE_REPORT_TEMPLATES)
 *   POST /api/v1/reports/templates/{id}/execute           (EXPORT_REPORTS)
 *   GET  /api/v1/reports/executions                       (EXPORT_REPORTS)
 *   GET  /api/v1/reports/executions/{id}/download         (EXPORT_REPORTS)
 *   GET  /api/v1/reports/data-quality                     (EXPORT_REPORTS)
 *   GET  /api/v1/exports/{id}                             (EXPORT_REPORTS)
 *   GET  /api/v1/analytics/operations-summary             (EXPORT_REPORTS)
 *
 * The full happy-path drives create-metric → create-template → execute-template →
 * /executions list → /exports/{id} → /executions/{id}/download.
 */
test.describe('Reports / metric definitions / templates / executions', () => {
  test('admin can list metric definitions', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/reports/metric-definitions');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('admin can create then list a metric definition', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const name = 'metric_' + Date.now();
    const create = await request.post('/api/v1/reports/metric-definitions', {
      headers: adminCsrf,
      data: {
        name,
        description: 'Test metric',
        queryTemplate: 'SELECT 1',
        domain: 'OPERATIONS',
      },
    });
    expect(create.status()).toBe(201);
    const m = await create.json();
    expect(m.id).toBeGreaterThan(0);
    expect(m.name).toBe(name);
    expect(m.domain).toBe('OPERATIONS');

    const list = await request.get('/api/v1/reports/metric-definitions');
    expect(list.status()).toBe(200);
    const arr = await list.json();
    expect(arr.some((x: any) => x.id === m.id && x.name === name)).toBe(true);
  });

  test('admin can list report templates', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/reports/templates');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('end-to-end: create metric → create template → execute → list → /exports/{id} → download', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const metricName = 'e2e_metric_' + Date.now();
    const metric = await request.post('/api/v1/reports/metric-definitions', {
      headers: adminCsrf,
      data: {
        name: metricName,
        description: 'e2e',
        queryTemplate: 'SELECT 1 AS value',
        domain: 'OPERATIONS',
      },
    });
    const metricId = (await metric.json()).id;

    const tmpl = await request.post('/api/v1/reports/templates', {
      headers: adminCsrf,
      data: {
        name: 'tmpl_' + Date.now(),
        description: 'template e2e',
        metricIds: String(metricId),
        filters: '{}',
        format: 'CSV',
      },
    });
    expect(tmpl.status()).toBe(201);
    const tmplBody = await tmpl.json();
    expect(tmplBody.id).toBeGreaterThan(0);
    expect(tmplBody.outputFormat).toBe('CSV');

    // Execute.
    const exec = await request.post(`/api/v1/reports/templates/${tmplBody.id}/execute`, {
      headers: adminCsrf,
      data: { filters: '{}', format: 'CSV' },
    });
    expect(exec.status()).toBe(201);
    const execBody = await exec.json();
    expect(execBody.id).toBeGreaterThan(0);
    expect(execBody.templateId).toBe(tmplBody.id);
    expect(execBody.outputFormat).toBe('CSV');

    // List executions.
    const list = await request.get('/api/v1/reports/executions');
    expect(list.status()).toBe(200);
    const arr = await list.json();
    expect(Array.isArray(arr)).toBe(true);
    expect(arr.some((e: any) => e.id === execBody.id)).toBe(true);

    // Get export by id (object-level: only the executor sees it).
    const exp = await request.get(`/api/v1/exports/${execBody.id}`);
    expect(exp.status()).toBe(200);
    const expBody = await exp.json();
    expect(expBody.id).toBe(execBody.id);
    expect(expBody.executedBy).toBeGreaterThan(0);

    // Download (only if the export produced a file).
    if (execBody.exportFilePath) {
      const dl = await request.get(`/api/v1/reports/executions/${execBody.id}/download`);
      expect(dl.status()).toBe(200);
      const ct = dl.headers()['content-type'];
      expect(ct).toMatch(/csv|pdf|octet-stream/);
      const buf = await dl.body();
      expect(buf.length).toBeGreaterThan(0);
    }
  });

  test('PARTICIPANT cannot list metric definitions (403)', async ({ request }) => {
    const u = uniqueUser('nomd');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/reports/metric-definitions');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot list report templates (403)', async ({ request }) => {
    const u = uniqueUser('notmpl');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/reports/templates');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot list executions (403)', async ({ request }) => {
    const u = uniqueUser('noexec');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/reports/executions');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot download an export (403)', async ({ request }) => {
    const u = uniqueUser('nodl');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/reports/executions/1/download');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot read /exports/{id} (403)', async ({ request }) => {
    const u = uniqueUser('noexpid');
    await registerAndLogin(request, u);
    const res = await request.get('/api/v1/exports/1');
    expect(res.status()).toBe(403);
  });

  test('admin gets data-quality summary by date range', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/reports/data-quality?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('admin gets analytics operations-summary by date range', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/analytics/operations-summary?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('exports: 404/400 for non-existent id (admin context)', async ({ request }) => {
    await loginAsSeeded(request, 'admin');
    const res = await request.get('/api/v1/exports/9999999');
    // Service throws on missing → 400 by mapper. (Or 404 if implementation differs.)
    expect([400, 404]).toContain(res.status());
  });

  test('metric create validates body — missing queryTemplate → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/reports/metric-definitions', {
      headers: adminCsrf,
      data: { name: 'x', domain: 'OPERATIONS' },
    });
    expect(res.status()).toBe(400);
  });
});
