import { test, expect, APIRequestContext } from '@playwright/test';
import { loginBrowser, registerViaApi } from './seed-helpers';

const SS = 'screenshots/positive-admin';

/** Seed helper: login via API request context, get CSRF, return token */
async function apiLoginCsrf(request: APIRequestContext, username: string): Promise<string> {
  const lr = await request.post('/api/v1/auth/login', { data: { username, password: 'SecurePass99' } });
  expect(lr.status()).toBe(200);
  const me = await request.get('/api/v1/auth/me');
  const hdrs = await me.headersArray();
  return hdrs.filter(h => h.name.toLowerCase() === 'set-cookie').map(h => h.value).join('; ').match(/XSRF-TOKEN=([^;]+)/)?.[1] || '';
}

test.describe.serial('Positive Admin Flows', () => {

  test('admin sidebar renders all 14 admin links', async ({ page }) => {
    await loginBrowser(page, 'e2e_admin', SS, '01-');
    for (const link of ['Dashboard', 'Verification Queue', 'Role Approvals', 'Blacklist', 'Appeals',
      'Fulfillment', 'Alerts', 'Work Orders', 'Analytics', 'Reports', 'Audit Logs', 'Policies',
      'Password Resets', 'Registration Review']) {
      await expect(page.locator('body')).toContainText(link);
    }
    await page.screenshot({ path: `${SS}/01-admin-sidebar.png`, fullPage: true });
  });

  test('admin verification queue — approve person verification', async ({ page, request }) => {
    const partUser = await registerViaApi(page, 'pvadm');
    const csrf = await apiLoginCsrf(request, partUser);
    const pvRes = await request.post('/api/v1/verification/person', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { legalName: 'Verify Me Person', dateOfBirth: '1992-03-10' },
    });
    expect([201, 202].includes(pvRes.status())).toBeTruthy();
    await request.post('/api/v1/auth/logout');

    await loginBrowser(page, 'e2e_admin', SS, '02-');
    await page.click('a:has-text("Verification Queue")');
    await page.waitForTimeout(2000);
    const queueItems = page.locator('.queue-item');
    expect(await queueItems.count()).toBeGreaterThan(0);
    await expect(queueItems.first().locator('.item-type')).toContainText('PERSON');
    await queueItems.first().locator('select').first().selectOption('APPROVE');
    await queueItems.first().locator('input[placeholder="Reason code"]').fill('VALID_ID');
    await page.screenshot({ path: `${SS}/02-verification-decision.png` });
    await queueItems.first().locator('button:has-text("Submit Decision")').click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/02-verification-approved.png` });
    await expect(queueItems.first()).toContainText('Decision submitted successfully');
  });

  test('admin role approval — approve a VOLUNTEER role request', async ({ page, request }) => {
    const roleUser = await registerViaApi(page, 'roleadm');
    const csrf = await apiLoginCsrf(request, roleUser);
    await request.post('/api/v1/accounts/me/role-requests', {
      headers: { 'X-XSRF-TOKEN': csrf }, data: { role: 'VOLUNTEER' },
    });
    await request.post('/api/v1/auth/logout');

    await loginBrowser(page, 'e2e_admin', SS, '03-');
    await page.click('a:has-text("Role Approvals")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/03-role-queue.png` });
    const items = page.locator('.request-item, [class*="request"]');
    expect(await items.count()).toBeGreaterThan(0);
    await items.first().locator('select').first().selectOption('APPROVE');
    await items.first().locator('button:has-text("Submit"), button:has-text("Decide")').click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/03-role-approved.png` });
  });

  test('admin work orders — seed WO, transition to IN_PROGRESS, add note', async ({ page, request }) => {
    // Seed a work order via API
    const csrf = await apiLoginCsrf(request, 'e2e_admin');
    const woRes = await request.post('/api/v1/work-orders', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { title: 'E2E Broken Pipe', severity: 'HIGH' },
    });
    expect(woRes.status()).toBe(201);
    await request.post('/api/v1/auth/logout');

    // Admin opens work order panel in browser
    await loginBrowser(page, 'e2e_admin', SS, '04-');
    await page.click('a:has-text("Work Orders")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/04-workorders-loaded.png` });
    await expect(page.locator('body')).toContainText('E2E Broken Pipe');
    await expect(page.locator('body')).toContainText('HIGH');
    // Find a WO in NEW_ALERT state (the one seeded in this test run)
    const woItems = page.locator('.wo-item');
    expect(await woItems.count()).toBeGreaterThan(0);
    // Target the last WO (the freshly seeded one)
    const woItem = woItems.last();
    await woItem.scrollIntoViewIfNeeded();
    await expect(woItem).toContainText('NEW_ALERT');

    // Transition to ACKNOWLEDGED
    await woItem.locator('button:has-text("ACKNOWLEDGED")').click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/04-wo-transitioned.png` });
    await expect(woItem).toContainText('Updated');

    // Add a note
    await woItem.locator('input[placeholder*="note"]').fill('Dispatched plumber to site');
    await woItem.locator('button:has-text("Add Note")').click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${SS}/04-wo-note-added.png` });
  });

  test('admin reports — create metric, create template, execute report, verify download link', async ({ page }) => {
    await loginBrowser(page, 'e2e_admin', SS, '05-');
    await page.click('a:has-text("Reports")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/05-reports-loaded.png` });
    // Verify page loads without error
    await expect(page.locator('.error-message')).not.toBeVisible();
    await expect(page.locator('h2').first()).toContainText('Create Metric Definition');

    // Create metric
    await page.locator('input').first().fill('E2E Account Count');
    await page.locator('textarea').first().fill('SELECT COUNT(*) FROM account');
    await page.screenshot({ path: `${SS}/05-metric-form.png` });
    await page.locator('button:has-text("Create Metric")').click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/05-metric-created.png` });
    await expect(page.locator('.success-message').first()).toContainText('Metric created');

    // Create template
    await page.locator('section').nth(1).locator('input').first().fill('E2E Weekly');
    await page.locator('section').nth(1).locator('input').nth(1).fill('1');
    await page.screenshot({ path: `${SS}/05-template-form.png` });
    await page.locator('button:has-text("Create Template")').click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/05-template-created.png` });
    await expect(page.locator('body')).toContainText('Template created');

    // Execute report — select the template from dropdown
    const execSelect = page.locator('section').nth(2).locator('select');
    await execSelect.selectOption({ index: 1 });
    await page.screenshot({ path: `${SS}/05-execute-form.png` });
    await page.locator('button:has-text("Execute")').click();
    await page.waitForTimeout(3000);
    await page.screenshot({ path: `${SS}/05-report-executed.png` });
    await expect(page.locator('.execution-result')).toContainText('COMPLETED');

    // Verify download link exists and points to correct endpoint
    const downloadLink = page.locator('.execution-result a');
    await expect(downloadLink).toBeVisible();
    const href = await downloadLink.getAttribute('href');
    expect(href).toMatch(/\/api\/v1\/reports\/executions\/\d+\/download/);
    await page.screenshot({ path: `${SS}/05-download-link.png` });
  });

  test('admin registration review — approve a pending registration', async ({ page, request }) => {
    // Seed: event with manualReview + participant registration
    const orgCsrf = await apiLoginCsrf(request, 'e2e_org');
    const evtRes = await request.post('/api/v1/events', {
      headers: { 'X-XSRF-TOKEN': orgCsrf },
      data: { organizationId: 'org-rev', title: 'Review Event', startAt: '2026-09-01T09:00:00', endAt: '2026-09-01T12:00:00', capacity: 10, manualReviewRequired: true, status: 'PUBLISHED' },
    });
    expect(evtRes.status()).toBe(201);
    const eventId = (await evtRes.json()).id;
    await request.post('/api/v1/auth/logout');

    const partCsrf = await apiLoginCsrf(request, 'e2e_participant');
    const regRes = await request.post(`/api/v1/events/${eventId}/registrations`, {
      headers: { 'X-XSRF-TOKEN': partCsrf },
      data: { formResponses: '{}' },
    });
    expect(regRes.status()).toBe(201);
    const regBody = await regRes.json();
    expect(regBody.status).toBe('PENDING_REVIEW');
    await request.post('/api/v1/auth/logout');

    // Admin reviews in browser
    await loginBrowser(page, 'e2e_admin', SS, '06-');
    await page.click('a:has-text("Registration Review")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/06-registration-review.png` });
    const regItems = page.locator('.reg-item');
    expect(await regItems.count()).toBeGreaterThan(0);

    // Click Approve on the last item (the one seeded in this test run)
    const lastItem = regItems.last();
    await expect(lastItem).toContainText('PENDING_REVIEW');
    await lastItem.locator('button:has-text("Approve")').click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${SS}/06-registration-approved.png` });
    await expect(lastItem).toContainText('Decision submitted');
  });
});
