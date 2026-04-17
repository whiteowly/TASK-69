import { test, expect } from '@playwright/test';
import { Buffer } from 'node:buffer';
import {
  getCsrfHeaders,
  login,
  loginAsSeeded,
  registerAccountWithType,
  registerAndLogin,
  uniqueUser,
} from './helpers';

test.describe('Remaining API endpoint coverage', () => {
  test('PUT /accounts/me/active-role switches to approved PARTICIPANT role', async ({ request }) => {
    const username = uniqueUser('switchrole');
    const userCsrf = await registerAndLogin(request, username);

    const req = await request.post('/api/v1/accounts/me/role-requests', {
      headers: userCsrf,
      data: { role: 'PARTICIPANT' },
    });
    expect(req.status()).toBe(201);
    const membershipId = (await req.json()).id;

    const adminCsrf = await loginAsSeeded(request, 'admin');
    const approve = await request.post(`/api/v1/admin/roles/${membershipId}/decision`, {
      headers: adminCsrf,
      data: { decision: 'APPROVE' },
    });
    expect(approve.status()).toBe(200);

    await login(request, username);
    const switchCsrf = await getCsrfHeaders(request);
    const sw = await request.put('/api/v1/accounts/me/active-role', {
      headers: switchCsrf,
      data: { role: 'PARTICIPANT' },
    });

    expect(sw.status()).toBe(200);
    const body = await sw.json();
    expect(body.activeRole).toBe('PARTICIPANT');
    expect(Array.isArray(body.permissions)).toBe(true);
  });

  test('POST /verification/org-documents + GET admin /org-document/{id}/download', async ({ request }) => {
    const orgUser = uniqueUser('orgdoc');
    const reg = await registerAccountWithType(request, orgUser, 'ORGANIZATION');
    expect(reg.status()).toBe(201);

    await login(request, orgUser);
    const orgCsrf = await getCsrfHeaders(request);

    const upload = await request.post('/api/v1/verification/org-documents', {
      headers: orgCsrf,
      multipart: {
        file: {
          name: 'credential.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('%PDF-1.4\n% org credential\n', 'utf-8'),
        },
      },
    });
    expect(upload.status()).toBe(201);
    const doc = await upload.json();
    expect(doc.id).toBeGreaterThan(0);

    await loginAsSeeded(request, 'admin');
    const download = await request.get(`/api/v1/admin/verification/org-document/${doc.id}/download`);
    expect(download.status()).toBe(200);
    expect(download.headers()['content-type']).toContain('application/pdf');
    const bytes = await download.body();
    expect(bytes.length).toBeGreaterThan(0);
  });

  test('resource upload + download entitlement + file bytes endpoint', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');

    const upload = await request.post('/api/v1/resources/upload', {
      headers: adminCsrf,
      multipart: {
        file: {
          name: 'resource.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('%PDF-1.4\n% downloadable resource\n', 'utf-8'),
        },
        title: 'Downloadable Resource ' + Date.now(),
        description: 'Resource upload endpoint coverage',
        fileVersion: 'v1',
        organizationId: 'ORG-1',
        status: 'PUBLISHED',
      },
    });

    expect(upload.status()).toBe(201);
    const resource = await upload.json();
    expect(resource.id).toBeGreaterThan(0);
    expect(resource.type).toBe('DOWNLOADABLE_FILE');

    const participant = uniqueUser('dluser');
    const pCsrf = await registerAndLogin(request, participant);

    const dlPermit = await request.post(`/api/v1/resources/files/${resource.id}/download`, {
      headers: pCsrf,
      data: { fileVersion: 'v1' },
    });
    expect(dlPermit.status()).toBe(200);
    const permitBody = await dlPermit.json();
    expect(permitBody.id).toBeGreaterThan(0);

    const fileRes = await request.get(`/api/v1/resources/${resource.id}/file`);
    expect(fileRes.status()).toBe(200);
    expect(fileRes.headers()['content-type']).toContain('application/pdf');
    const fileBytes = await fileRes.body();
    expect(fileBytes.length).toBeGreaterThan(0);
  });

  test('POST /work-orders/{id}/photos uploads image evidence', async ({ request }) => {
    const adminCsrf = await loginAsSeeded(request, 'admin');

    const create = await request.post('/api/v1/work-orders', {
      headers: adminCsrf,
      data: {
        title: 'Photo Evidence WO ' + Date.now(),
        description: 'Testing photo upload endpoint',
        severity: 'MEDIUM',
        organizationId: 'ORG-1',
      },
    });
    expect(create.status()).toBe(201);
    const woId = (await create.json()).id;

    const photo = await request.post(`/api/v1/work-orders/${woId}/photos`, {
      headers: adminCsrf,
      multipart: {
        file: {
          name: 'evidence.jpg',
          mimeType: 'image/jpeg',
          buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
        },
      },
    });

    expect(photo.status()).toBe(201);
    const body = await photo.json();
    expect(body.workOrderId).toBe(woId);
    expect(body.fileName).toBe('evidence.jpg');
    expect(body.contentType).toBe('image/jpeg');
  });
});
