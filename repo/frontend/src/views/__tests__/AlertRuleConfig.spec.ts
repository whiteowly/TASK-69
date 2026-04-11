import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import AlertRuleConfig from '@/views/admin/AlertRuleConfig.vue'
import apiClient from '@/api/client'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  getCsrfToken: vi.fn(() => null),
}))

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: AlertRuleConfig }],
  })
}

describe('AlertRuleConfig', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders alert rule config heading and form', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: { defaults: [], overrides: [] } })
    const wrapper = mount(AlertRuleConfig, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('h1').text()).toContain('Alert')
  })

  it('displays loaded default rules', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: {
        defaults: [
          { id: 1, alertType: 'OVER_TEMPERATURE', severity: 'HIGH', thresholdOperator: 'GT', thresholdValue: 120, cooldownSeconds: 60 },
        ],
        overrides: [],
      },
    })
    const wrapper = mount(AlertRuleConfig, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('OVER_TEMPERATURE')
    expect(wrapper.text()).toContain('HIGH')
  })
})
