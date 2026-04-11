import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import EventBrowse from '@/views/participant/EventBrowse.vue'
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
    routes: [{ path: '/', component: EventBrowse }],
  })
}

describe('EventBrowse', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('displays upcoming events', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [
        { id: 1, title: 'Community Meal', mode: 'ON_SITE', startAt: '2026-05-01T17:00:00', capacity: 50, status: 'PUBLISHED' },
      ],
    })
    const wrapper = mount(EventBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Community Meal')
    expect(wrapper.text()).toContain('ON_SITE')
  })

  it('shows register button for events', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, title: 'Event', mode: 'ON_SITE', startAt: '2026-05-01T17:00:00', capacity: 50, status: 'PUBLISHED' }],
    })
    const wrapper = mount(EventBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const registerBtn = wrapper.findAll('button').find(b => b.text().includes('Register'))
    expect(registerBtn).toBeDefined()
  })

  it('calls registration API on register click', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 3, title: 'Event', mode: 'ON_SITE', startAt: '2026-05-01T17:00:00', capacity: 50, status: 'PUBLISHED' }],
    })
    ;(apiClient.post as any).mockResolvedValue({ data: { id: 1, status: 'APPROVED' } })

    const wrapper = mount(EventBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const registerBtn = wrapper.findAll('button').find(b => b.text().includes('Register'))
    await registerBtn!.trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalled()
  })
})
