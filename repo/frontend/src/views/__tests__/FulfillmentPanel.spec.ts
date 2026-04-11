import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import FulfillmentPanel from '@/views/admin/FulfillmentPanel.vue'
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
    routes: [{ path: '/', component: FulfillmentPanel }],
  })
}

describe('FulfillmentPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('displays orders with status badges and overdue detection', async () => {
    const oldDate = new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString()
    ;(apiClient.get as any).mockResolvedValue({
      data: [
        { id: 1, status: 'PACKED', statusChangedAt: oldDate, createdAt: oldDate, updatedAt: oldDate, fulfillmentType: 'PHYSICAL_SHIPMENT' },
        { id: 2, status: 'ORDERED', statusChangedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), fulfillmentType: 'PHYSICAL_SHIPMENT' },
      ],
    })
    const wrapper = mount(FulfillmentPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Order #1')
    expect(wrapper.text()).toContain('OVERDUE')
    expect(wrapper.text()).toContain('Order #2')
  })

  it('shows transition buttons matching valid next states', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, status: 'ORDERED', createdAt: new Date().toISOString() }],
    })
    const wrapper = mount(FulfillmentPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const buttons = wrapper.findAll('.button-group button')
    expect(buttons.length).toBe(1)
    expect(buttons[0].text()).toBe('ALLOCATED')
  })

  it('calls transition API when state button clicked', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 5, status: 'ALLOCATED', createdAt: new Date().toISOString() }],
    })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })

    const wrapper = mount(FulfillmentPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const packedBtn = wrapper.findAll('.button-group button').find(b => b.text() === 'PACKED')
    expect(packedBtn).toBeDefined()
    await packedBtn!.trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/reward-orders/5/transition',
      expect.objectContaining({ toState: 'PACKED' })
    )
  })
})
