import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('has correct initial state', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accountId).toBeNull()
    expect(auth.username).toBe('')
    expect(auth.activeRole).toBe('')
    expect(auth.permissions).toEqual([])
    expect(auth.accountStatus).toBe('')
  })

  it('can be created', () => {
    const auth = useAuthStore()
    expect(auth).toBeDefined()
  })
})
