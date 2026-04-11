import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RoleApprovalPanel from '@/views/admin/RoleApprovalPanel.vue'
import apiClient from '@/api/client'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  getCsrfToken: vi.fn(() => null),
}))

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: RoleApprovalPanel }],
  })
}

const mockRequests = [
  { id: 10, accountId: 42, roleType: 'VOLUNTEER', scopeId: null, status: 'REQUESTED', createdAt: '2026-04-09T10:00:00Z' },
  { id: 11, accountId: 99, roleType: 'ORG_OPERATOR', scopeId: 'org_5', status: 'REQUESTED', createdAt: '2026-04-09T11:00:00Z' },
]

describe('RoleApprovalPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders pending role requests with account and role info', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockRequests })
    const wrapper = mount(RoleApprovalPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Account #42')
    expect(wrapper.text()).toContain('VOLUNTEER')
    expect(wrapper.text()).toContain('Account #99')
    expect(wrapper.text()).toContain('ORG_OPERATOR')
    expect(wrapper.text()).toContain('Scope: org_5')
  })

  it('submits APPROVE decision and shows success', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockRequests[0]] })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })
    const wrapper = mount(RoleApprovalPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/admin/roles/10/decision',
      expect.objectContaining({ decision: 'APPROVE' })
    )
    expect(wrapper.text()).toContain('Decision submitted successfully')
  })

  it('shows error on failed decision', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockRequests[0]] })
    ;(apiClient.post as any).mockRejectedValue({ response: { data: { message: 'Already decided' } } })
    const wrapper = mount(RoleApprovalPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Already decided')
  })

  it('shows empty state when no pending requests', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(RoleApprovalPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No pending role requests')
  })
})
