import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import PolicyManagement from '@/views/admin/PolicyManagement.vue'
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
    routes: [{ path: '/', component: PolicyManagement }],
  })
}

describe('PolicyManagement', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('loads and displays policies', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [
        { id: 1, name: 'Water Limit', scope: 'HOUSEHOLD', maxActions: 2, windowDays: 30, resourceAction: 'CLAIM' },
      ],
    })
    const wrapper = mount(PolicyManagement, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Water Limit')
    expect(wrapper.text()).toContain('HOUSEHOLD')
    expect(wrapper.text()).toContain('Max 2 per 30 days')
  })

  it('renders create policy form with scope and action selects', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(PolicyManagement, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('#policy-scope').exists()).toBe(true)
    expect(wrapper.find('#policy-action').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').text()).toBe('Create Policy')
  })

  it('submits create policy form and reloads list', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })

    const wrapper = mount(PolicyManagement, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    await wrapper.find('#policy-name').setValue('Test Policy')
    await wrapper.find('#policy-max').setValue(3)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith('/resource-policies', expect.objectContaining({
      name: 'Test Policy',
      maxActions: 3,
    }))
    expect(wrapper.text()).toContain('Policy created.')
  })
})
