import { defineStore } from 'pinia'
import { ref } from 'vue'
import apiClient from '@/api/client'
import type { LoginResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const accountId = ref<number | null>(null)
  const username = ref<string>('')
  const activeRole = ref<string>('')
  const permissions = ref<string[]>([])
  const accountStatus = ref<string>('')
  const isAuthenticated = ref(false)

  /**
   * Attempts login. On success sets ACTIVE state. On blacklisted 423, the backend
   * still creates a constrained session (cookie is set), so we mark the store as
   * BLACKLISTED to let the router redirect to /appeal where the session allows
   * appeal submission.
   */
  async function login(usernameInput: string, password: string): Promise<void> {
    try {
      const response = await apiClient.post<LoginResponse>('/auth/login', {
        username: usernameInput,
        password,
      }, { _skipGlobal423: true } as any) // skip global 423 interceptor for login
      const data = response.data
      accountId.value = data.accountId
      username.value = data.username
      activeRole.value = data.activeRole
      permissions.value = data.permissions
      accountStatus.value = 'ACTIVE'
      isAuthenticated.value = true
    } catch (err: any) {
      const code = err.response?.data?.code
      if (code === 'ACCOUNT_BLACKLISTED') {
        // Backend created a constrained session (cookie set) — mark store as blacklisted
        accountStatus.value = 'BLACKLISTED'
        isAuthenticated.value = true // session exists, just constrained
      } else if (code === 'ACCOUNT_LOCKED') {
        accountStatus.value = 'LOCKED'
        isAuthenticated.value = false
      }
      throw err
    }
  }

  async function logout(): Promise<void> {
    try {
      await apiClient.post('/auth/logout')
    } finally {
      $reset()
    }
  }

  async function fetchMe(): Promise<void> {
    try {
      const response = await apiClient.get<LoginResponse>('/auth/me')
      const data = response.data
      accountId.value = data.accountId
      username.value = data.username
      activeRole.value = data.activeRole
      permissions.value = data.permissions
      accountStatus.value = 'ACTIVE'
      isAuthenticated.value = true
    } catch (err: any) {
      const code = err.response?.data?.code
      if (code === 'ACCOUNT_BLACKLISTED') {
        accountStatus.value = 'BLACKLISTED'
        isAuthenticated.value = true
      }
      throw err
    }
  }

  async function register(usernameInput: string, password: string, accountType: 'PERSON' | 'ORGANIZATION'): Promise<void> {
    await apiClient.post('/auth/register', { username: usernameInput, password, accountType })
  }

  function $reset() {
    accountId.value = null
    username.value = ''
    activeRole.value = ''
    permissions.value = []
    accountStatus.value = ''
    isAuthenticated.value = false
  }

  async function switchRole(role: string, scopeId?: string): Promise<void> {
    const response = await apiClient.put<LoginResponse>('/accounts/me/active-role', { role, scopeId })
    const data = response.data
    activeRole.value = data.activeRole
    permissions.value = data.permissions
  }

  return {
    accountId,
    username,
    activeRole,
    permissions,
    accountStatus,
    isAuthenticated,
    login,
    logout,
    fetchMe,
    register,
    switchRole,
    $reset,
  }
})
