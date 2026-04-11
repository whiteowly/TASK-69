import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/**
 * Tests the ACTUAL production router module (not a copy).
 * Uses the real route definitions and guard logic from @/router/index.ts.
 */
describe('production router guards', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    // Reset router to a known state
    await router.push('/login')
    await router.isReady()
  })

  // --- Public route access ---

  it('allows unauthenticated access to /login', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('allows unauthenticated access to /register', async () => {
    await router.push('/register')
    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('allows unauthenticated access to /appeal', async () => {
    await router.push('/appeal')
    expect(router.currentRoute.value.path).toBe('/appeal')
  })

  it('allows unauthenticated access to /locked', async () => {
    await router.push('/locked')
    expect(router.currentRoute.value.path).toBe('/locked')
  })

  // --- Unauthenticated redirect ---

  it('redirects unauthenticated user from workspace to /login', async () => {
    await router.push('/workspace/PARTICIPANT')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  // --- Locked vs blacklisted routing ---

  it('redirects LOCKED user to /locked from workspace', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'LOCKED'
    auth.activeRole = 'PARTICIPANT'

    await router.push('/workspace/PARTICIPANT')
    expect(router.currentRoute.value.path).toBe('/locked')
  })

  it('redirects BLACKLISTED user to /appeal from workspace (not /locked)', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'BLACKLISTED'
    auth.activeRole = 'PARTICIPANT'

    await router.push('/workspace/PARTICIPANT')
    expect(router.currentRoute.value.path).toBe('/appeal')
  })

  // --- Admin route protection ---

  it('allows ADMIN role to access /workspace/admin', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'ACTIVE'
    auth.activeRole = 'ADMIN'

    await router.push('/workspace/admin')
    expect(router.currentRoute.value.path).toBe('/workspace/admin')
  })

  it('redirects PARTICIPANT from /workspace/admin to their workspace', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'ACTIVE'
    auth.activeRole = 'PARTICIPANT'

    await router.push('/workspace/admin')
    expect(router.currentRoute.value.path).toBe('/workspace/PARTICIPANT')
  })

  it('redirects VOLUNTEER from /workspace/admin/blacklist to their workspace', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'ACTIVE'
    auth.activeRole = 'VOLUNTEER'

    await router.push('/workspace/admin/blacklist')
    expect(router.currentRoute.value.path).toBe('/workspace/VOLUNTEER')
  })

  it('allows ADMIN to access nested admin route /workspace/admin/appeals', async () => {
    const auth = useAuthStore()
    auth.isAuthenticated = true
    auth.accountStatus = 'ACTIVE'
    auth.activeRole = 'ADMIN'

    await router.push('/workspace/admin/appeals')
    expect(router.currentRoute.value.path).toBe('/workspace/admin/appeals')
  })
})
