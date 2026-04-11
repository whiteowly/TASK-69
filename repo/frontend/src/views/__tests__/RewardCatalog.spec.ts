import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RewardCatalog from '@/views/participant/RewardCatalog.vue'
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
    routes: [{ path: '/', component: RewardCatalog }],
  })
}

describe('RewardCatalog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders reward catalog heading', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(RewardCatalog, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Reward Catalog')
  })

  it('sends address with correct field names (state/zip not stateCode/zipCode)', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, title: 'Kit', tier: 'GOLD', inventoryCount: 5, perUserLimit: 2, fulfillmentType: 'SHIP', status: 'ACTIVE' }],
    })
    ;(apiClient.post as any).mockImplementation((url: string) => {
      if (url.includes('addresses')) return Promise.resolve({ data: { id: 99 } })
      return Promise.resolve({ data: { id: 1, status: 'ORDERED' } })
    })

    const wrapper = mount(RewardCatalog, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    // Fill address form
    const inputs = wrapper.findAll('input')
    // line1, line2, city, state, zip + quantity
    await inputs[1].setValue('123 Main St')  // line1
    await inputs[3].setValue('Springfield')   // city
    await inputs[4].setValue('IL')            // state
    await inputs[5].setValue('62701')         // zip

    const btn = wrapper.findAll('button').find(b => b.text().includes('Place Order'))
    await btn!.trigger('click')
    await flushPromises()

    // Verify address creation call uses state/zip (not stateCode/zipCode)
    const addressCall = (apiClient.post as any).mock.calls.find((c: any[]) => c[0].includes('addresses'))
    expect(addressCall).toBeDefined()
    expect(addressCall[1]).toHaveProperty('state')
    expect(addressCall[1]).toHaveProperty('zip')
    expect(addressCall[1]).not.toHaveProperty('stateCode')
    expect(addressCall[1]).not.toHaveProperty('zipCode')
  })
})
