import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RegistrationReview from '@/views/admin/RegistrationReview.vue'
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
    routes: [{ path: '/', component: RegistrationReview }],
  })
}

describe('RegistrationReview', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('displays pending registrations', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [
        { id: 10, eventId: 1, eventTitle: 'Community Meal', accountId: 42, status: 'PENDING_REVIEW', createdAt: '2026-04-01T10:00:00' },
      ],
    })
    const wrapper = mount(RegistrationReview, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Community Meal')
    expect(wrapper.text()).toContain('PENDING_REVIEW')
    expect(wrapper.text()).toContain('Approve')
    expect(wrapper.text()).toContain('Deny')
  })

  it('calls decision API with APPROVE', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 10, eventId: 1, eventTitle: 'Event', accountId: 42, status: 'PENDING_REVIEW', createdAt: '2026-04-01T10:00:00' }],
    })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })

    const wrapper = mount(RegistrationReview, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const approveBtn = wrapper.findAll('button').find(b => b.text() === 'Approve')
    await approveBtn!.trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith('/registrations/10/decision', { decision: 'APPROVE' })
    expect(wrapper.text()).toContain('Decision submitted.')
  })

  it('shows empty state when no pending registrations', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(RegistrationReview, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No pending registrations.')
  })
})
