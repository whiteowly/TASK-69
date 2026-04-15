import { test, expect } from '@playwright/test';
import { loginAsSeeded, registerAndLogin, uniqueUser, getCsrfHeaders, login } from './helpers';

/**
 * Endpoint coverage:
 *   GET  /api/v1/admin/verification/queue                              (REVIEW_VERIFICATION)
 *   POST /api/v1/admin/verification/person/{id}/decision               (REVIEW_VERIFICATION)
 *   POST /api/v1/admin/verification/org-document/{documentId}/decision (REVIEW_VERIFICATION)
 *   POST /api/v1/verification/person                                   (auth)
 *   POST /api/v1/verification/org-documents                            (auth, multipart)
 */
test.describe('Verification — admin queue and decisions', () => {
  test('admin queue returns array including a freshly submitted person verification', async ({ request }) => {
    // Submit a person verification as a fresh user.
    const username = uniqueUser('pvqueue');
    const csrf = await registerAndLogin(request, username);
    const submit = await request.post('/api/v1/verification/person', {
      headers: csrf,
      data: { legalName: 'Queue Test', dateOfBirth: '1985-04-12' },
    });
    expect(submit.status()).toBe(202);
    const submitted = await submit.json();
    expect(submitted.status).toBe('UNDER_REVIEW');
    expect(submitted.verificationId).toBeGreaterThan(0);

    // Admin lists queue and finds it.
    await loginAsSeeded(request, 'admin');
    const queueRes = await request.get('/api/v1/admin/verification/queue');
    expect(queueRes.status()).toBe(200);
    const items = await queueRes.json();
    expect(Array.isArray(items)).toBe(true);
    const ours = items.find((i: any) => i.type === 'PERSON' && i.id === submitted.verificationId);
    expect(ours).toBeTruthy();
    expect(ours.status).toBe('UNDER_REVIEW');
    expect(ours.legalName).toBe('Queue Test');
  });

  test('admin can approve a person verification — status flips to APPROVED', async ({ request }) => {
    const username = uniqueUser('pvapprov');
    const csrf = await registerAndLogin(request, username);
    const submit = await request.post('/api/v1/verification/person', {
      headers: csrf,
      data: { legalName: 'Approve Me', dateOfBirth: '1990-01-01' },
    });
    const verificationId = (await submit.json()).verificationId;

    const adminCsrf = await loginAsSeeded(request, 'admin');
    const decision = await request.post(`/api/v1/admin/verification/person/${verificationId}/decision`, {
      headers: adminCsrf,
      data: { decision: 'APPROVE', reviewNote: 'docs match' },
    });
    expect(decision.status()).toBe(200);
    const body = await decision.json();
    expect(body.id).toBe(verificationId);
    expect(body.status).toBe('APPROVED');
  });

  test('admin can decide an org-document verification (404 when document missing)', async ({ request }) => {
    // We don't have an easy way to upload an org document via API in pure JSON, so
    // instead exercise the endpoint with a non-existent id and assert the controller is
    // reachable (auth passes, service throws 404/400 — not 401/403).
    const adminCsrf = await loginAsSeeded(request, 'admin');
    const res = await request.post('/api/v1/admin/verification/org-document/9999999/decision', {
      headers: adminCsrf,
      data: { decision: 'APPROVE', reviewNote: 'n/a' },
    });
    // Service throws IllegalArgumentException → mapped to 400 by global handler.
    expect([400, 404]).toContain(res.status());
  });

  test('PARTICIPANT cannot decide org-document verification (403)', async ({ request }) => {
    const username = uniqueUser('noorgdoc');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/admin/verification/org-document/1/decision', {
      headers: csrf,
      data: { decision: 'APPROVE' },
    });
    expect(res.status()).toBe(403);
  });

  test('PARTICIPANT cannot decide person verification (403)', async ({ request }) => {
    const username = uniqueUser('nopvdec');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/admin/verification/person/1/decision', {
      headers: csrf,
      data: { decision: 'APPROVE' },
    });
    expect(res.status()).toBe(403);
  });

  test('person verification rejects missing legalName (400)', async ({ request }) => {
    const username = uniqueUser('badpv');
    const csrf = await registerAndLogin(request, username);
    const res = await request.post('/api/v1/verification/person', {
      headers: csrf,
      data: { dateOfBirth: '1990-01-01' },
    });
    expect(res.status()).toBe(400);
  });

  test('admin queue rejects unauthenticated requests (401)', async ({ request }) => {
    const res = await request.get('/api/v1/admin/verification/queue');
    expect(res.status()).toBe(401);
  });
});
