import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ResourceBrowse from '@/views/participant/ResourceBrowse.vue'
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
    routes: [{ path: '/', component: ResourceBrowse }],
  })
}

describe('ResourceBrowse', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('displays published resources', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [
        { id: 1, type: 'CLAIMABLE_ITEM', title: 'Water Kit', inventoryCount: 50, status: 'PUBLISHED' },
        { id: 2, type: 'DOWNLOADABLE_FILE', title: 'Emergency Guide', status: 'PUBLISHED' },
      ],
    })
    const wrapper = mount(ResourceBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Water Kit')
    expect(wrapper.text()).toContain('Emergency Guide')
  })

  it('shows claim/download buttons per resource type', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, type: 'CLAIMABLE_ITEM', title: 'Water Kit', inventoryCount: 50, status: 'PUBLISHED' }],
    })
    const wrapper = mount(ResourceBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const claimBtn = wrapper.findAll('button').find(b => b.text().includes('Claim'))
    expect(claimBtn).toBeDefined()
  })

  it('printable notice link uses correct /print path', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, type: 'CLAIMABLE_ITEM', title: 'Kit', inventoryCount: 5, status: 'PUBLISHED' }],
    })
    ;(apiClient.post as any).mockResolvedValue({
      data: { result: 'ALLOWED', printableNoticeId: 42 },
    })
    const wrapper = mount(ResourceBrowse, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    // Click claim
    const claimBtn = wrapper.findAll('button').find(b => b.text().includes('Claim'))
    await claimBtn!.trigger('click')
    await flushPromises()

    const link = wrapper.find('a[href*="notices"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/api/v1/notices/42/print')
  })
})
