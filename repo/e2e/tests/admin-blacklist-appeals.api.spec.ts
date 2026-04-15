import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAccount, login, registerAndLogin, uniqueUser, whoAmI } from './helpers';

/**
 * Endpoint coverage:
 *   POST /api/v1/admin/blacklist                          (MANAGE_BLACKLIST)
 *   GET  /api/v1/admin/appeals                            (MANAGE_BLACKLIST)
 *   POST /api/v1/admin/appeals/{appealId}/decision        (MANAGE_BLACKLIST)
 *   GET  /api/v1/appeals/my-blacklist                     (auth)
 *   POST /api/v1/appeals                                  (auth, blacklisted-only path)
 *
 * We exercise the full blacklist → appeal → admin decision flow end-to-end so each
 * assertion verifies real persisted state, not just envelope fields.
 */
test.describe('Admin blacklist + appeals lifecycle', () => {
  test('admin can blacklist a user; user can read /my-blacklist; admin sees and decides appeal', async ({ request }) => {
    // Step 1: create a victim account, capture its id.
    const victimName = uniqueUser('victim');
    const victimReg = await registerAccount(request, victimName);
    expect(victimReg.status()).toBe(201);
    const victimId = (await victimReg.json()).accountId;
    expect(victimId).toBeGreaterThan(0);

    // Step 2: admin blacklists the victim.
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const blRes = await request.post('/api/v1/admin/blacklist', {
      headers: adminCsrf,
      data: { targetAccountId: victimId, reasonCode: 'ABUSE', note: 'test blacklist' },
    });
    expect(blRes.status()).toBe(201);
    const blacklist = await blRes.json();
    expect(blacklist.blacklistId).toBeGreaterThan(0);
    expect(blacklist.accountId).toBe(victimId);
    expect(blacklist.reasonCode).toBe('ABUSE');

    // Step 3: victim logs in — login returns 423 because they are blacklisted but cookie is set.
    const victimLoginRes = await login(request, victimName);
    expect(victimLoginRes.status()).toBe(423);
    const errBody = await victimLoginRes.json();
    expect(errBody.code).toBe('ACCOUNT_BLACKLISTED');

    // Step 4: victim reads /my-blacklist and sees their own record.
    const myBl = await request.get('/api/v1/appeals/my-blacklist');
    expect(myBl.status()).toBe(200);
    const myBlBody = await myBl.json();
    expect(myBlBody.blacklistId).toBe(blacklist.blacklistId);
    expect(myBlBody.reasonCode).toBe('ABUSE');

    // Step 5: victim submits an appeal. CSRF token comes from /me cookies.
    // Note: the constrained blacklisted session may reject /auth/me with 401.
    // Use the CSRF cookie set by the earlier login response instead by reading via blacklist GET.
    const headersArr = await myBl.headersArray();
    const setCookies = headersArr
      .filter(h => h.name.toLowerCase() === 'set-cookie')
      .map(h => h.value)
      .join('; ');
    const xsrfMatch = setCookies.match(/XSRF-TOKEN=([^;]+)/);
    const xsrf = xsrfMatch ? xsrfMatch[1] : 'missing';
    const appealRes = await request.post('/api/v1/appeals', {
      headers: { 'X-XSRF-TOKEN': xsrf },
      data: { blacklistId: blacklist.blacklistId, appealText: 'I want to come back', contactNote: 'email me' },
    });
    expect(appealRes.status()).toBe(201);
    const appeal = await appealRes.json();
    expect(appeal.appealId).toBeGreaterThan(0);
    expect(appeal.blacklistRecordId).toBe(blacklist.blacklistId);
    expect(appeal.accountId).toBe(victimId);

    // Step 6: admin lists appeals and sees ours; then approves.
    const adminCsrf2 = await loginAsSeeded(request, 'admin');
    const listRes = await request.get('/api/v1/admin/appeals');
    expect(listRes.status()).toBe(200);
    const appeals = await listRes.json();
    expect(Array.isArray(appeals)).toBe(true);
    const ours = appeals.find((a: any) => a.appealId === appeal.appealId);
    expect(ours).toBeTruthy();
    expect(ours.appealText).toBe('I want to come back');

    const decRes = await request.post(`/api/v1/admin/appeals/${appeal.appealId}/decision`, {
      headers: adminCsrf2,
      data: { decision: 'APPROVE_UNBLOCK', decisionNote: 'reinstate' },
    });
    expect(decRes.status()).toBe(200);
    const decided = await decRes.json();
    expect(decided.appealId).toBe(appeal.appealId);
    // After unblock-approval the appeal record stores the decision verb itself.
    expect(decided.status).toBe('APPROVE_UNBLOCK');
  });

  test('PARTICIPANT cannot blacklist (403)', async ({ request }) => {
    const username = uniqueUser('nobl');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/admin/blacklist', {
      headers: csrf,
      data: { targetAccountId: 9999, reasonCode: 'NO' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot list appeals (403)', async ({ request }) => {
    const username = uniqueUser('noapl');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/admin/appeals');
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot decide an appeal (403)', async ({ request }) => {
    const username = uniqueUser('nodecidapl');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/admin/appeals/1/decision', {
      headers: csrf,
      data: { decision: 'APPROVE' },
    });
    expect(res.status()).toBe(403);
  });

  test('non-blacklisted user gets 404 from /my-blacklist', async ({ request }) => {
    const username = uniqueUser('clean');
    await registerAndLogin(request, username);
    const res = await request.get('/api/v1/appeals/my-blacklist');
    expect(res.status()).toBe(404);
  });

  test('blacklist endpoint validates body — missing reasonCode → 400', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/admin/blacklist', {
      headers: adminCsrf,
      data: { targetAccountId: 1 },
    });
    expect(res.status()).toBe(400);
  });
});
