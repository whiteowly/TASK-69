import { APIRequestContext } from '@playwright/test';

/**
 * Registers a new account and returns the response.
 */
export async function registerAccount(
  request: APIRequestContext,
  username: string,
  password: string = 'TestPass123!'
) {
  const res = await request.post('/api/v1/auth/register', {
    data: { username, password, accountType: 'PERSON' },
  });
  return res;
}

/**
 * Registers a new account with explicit account type.
 */
export async function registerAccountWithType(
  request: APIRequestContext,
  username: string,
  accountType: 'PERSON' | 'ORGANIZATION',
  password: string = 'TestPass123!'
) {
  const res = await request.post('/api/v1/auth/register', {
    data: { username, password, accountType },
  });
  return res;
}

/**
 * Logs in and returns a cookie-bearing request context via the response.
 * Playwright automatically tracks cookies per context.
 */
export async function login(
  request: APIRequestContext,
  username: string,
  password: string = 'TestPass123!'
) {
  const res = await request.post('/api/v1/auth/login', {
    data: { username, password },
  });
  return res;
}

/**
 * Gets current XSRF token from cookies and returns headers needed for state-changing requests.
 */
export async function getCsrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  // The XSRF-TOKEN cookie is set by Spring Security on any response.
  // We need to read it and send it back as X-XSRF-TOKEN header.
  const meRes = await request.get('/api/v1/auth/me');
  const headersArr = await meRes.headersArray();
  const setCookieHeaders = headersArr
    .filter(h => h.name.toLowerCase() === 'set-cookie')
    .map(h => h.value);
  const allCookies = setCookieHeaders.join('; ');
  const xsrfMatch = allCookies.match(/XSRF-TOKEN=([^;]+)/);
  const token = xsrfMatch ? xsrfMatch[1] : 'missing';
  return { 'X-XSRF-TOKEN': token };
}

/**
 * Register + login helper. Returns CSRF headers for subsequent calls.
 */
export async function registerAndLogin(
  request: APIRequestContext,
  username: string,
  password: string = 'TestPass123!'
): Promise<Record<string, string>> {
  await registerAccount(request, username, password);
  await login(request, username, password);
  return getCsrfHeaders(request);
}

/** Unique username generator */
let counter = 0;
export function uniqueUser(prefix: string = 'user'): string {
  return `${prefix}_${Date.now()}_${counter++}`;
}

/**
 * Seeded credentials baked in by e2e/seed-e2e-data.sh.
 * These accounts have ADMIN / ORG_OPERATOR / PARTICIPANT roles approved.
 */
export const SEEDED = {
  admin: { username: 'e2e_admin', password: 'SecurePass99' },
  org: { username: 'e2e_org', password: 'SecurePass99' },
  participant: { username: 'e2e_participant', password: 'SecurePass99' },
};

/**
 * Login as a seeded role and return CSRF headers ready for state-changing requests.
 * Throws if the seeded account isn't available — the test suite assumes the seed has run.
 */
export async function loginAsSeeded(
  request: APIRequestContext,
  role: 'admin' | 'org' | 'participant'
): Promise<Record<string, string>> {
  const creds = SEEDED[role];
  const res = await login(request, creds.username, creds.password);
  if (res.status() !== 200) {
    throw new Error(
      `loginAsSeeded(${role}) failed with ${res.status()} — ensure e2e/seed-e2e-data.sh ran`
    );
  }
  return getCsrfHeaders(request);
}

/**
 * Read accountId from the /auth/me payload. Useful when tests need to
 * blacklist / target the currently-logged-in user.
 */
export async function whoAmI(
  request: APIRequestContext
): Promise<{ accountId: number; username: string; activeRole: string; approvedRoles: string[]; permissions: string[] }> {
  const res = await request.get('/api/v1/auth/me');
  if (res.status() !== 200) {
    throw new Error(`whoAmI: /me returned ${res.status()}`);
  }
  return res.json();
}
