import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import EventManagement from '@/views/org/EventManagement.vue'
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
    routes: [{ path: '/', component: EventManagement }],
  })
}

describe('EventManagement', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders event creation form with required fields', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(EventManagement, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('#event-title').exists()).toBe(true)
    expect(wrapper.find('#event-mode').exists()).toBe(true)
    expect(wrapper.find('#event-start').exists()).toBe(true)
    expect(wrapper.find('#event-end').exists()).toBe(true)
    expect(wrapper.find('#event-capacity').exists()).toBe(true)
    expect(wrapper.text()).toContain('Create Event')
    expect(wrapper.text()).toContain('Enable Waitlist')
    expect(wrapper.text()).toContain('Manual Review Required')
  })
})
