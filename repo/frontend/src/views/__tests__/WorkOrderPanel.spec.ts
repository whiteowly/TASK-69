import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import WorkOrderPanel from '@/views/admin/WorkOrderPanel.vue'
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
    routes: [{ path: '/', component: WorkOrderPanel }],
  })
}

describe('WorkOrderPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders work order panel heading', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(WorkOrderPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Work Order Panel')
  })
})
