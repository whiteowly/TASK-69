import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ReportPanel from '@/views/admin/ReportPanel.vue'
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
    routes: [{ path: '/', component: ReportPanel }],
  })
}

function mockFetchAll() {
  ;(apiClient.get as any).mockImplementation((url: string) => {
    if (url.includes('metrics')) return Promise.resolve({ data: [{ id: 1, name: 'Claims Count', query: 'SELECT COUNT(*) FROM claim_record' }] })
    if (url.includes('templates')) return Promise.resolve({ data: [{ id: 1, name: 'Monthly Summary', metricIds: [1] }] })
    if (url.includes('executions')) return Promise.resolve({ data: [] })
    return Promise.resolve({ data: [] })
  })
}

describe('ReportPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders report panel with sections', async () => {
    mockFetchAll()
    const wrapper = mount(ReportPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Report Panel')
    expect(wrapper.text()).toContain('Create Metric Definition')
    expect(wrapper.text()).toContain('Create Report Template')
    expect(wrapper.text()).toContain('Execute Report')
  })

  it('displays loaded templates in execute dropdown', async () => {
    mockFetchAll()
    const wrapper = mount(ReportPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const options = wrapper.findAll('select option')
    expect(options.some(o => o.text().includes('Monthly Summary'))).toBe(true)
  })

  it('shows past executions with download links', async () => {
    ;(apiClient.get as any).mockImplementation((url: string) => {
      if (url.includes('metrics')) return Promise.resolve({ data: [] })
      if (url.includes('templates')) return Promise.resolve({ data: [] })
      if (url.includes('executions')) return Promise.resolve({
        data: [{ id: 99, status: 'COMPLETED', exportFilePath: 'exports/report-1-abc.csv' }],
      })
      return Promise.resolve({ data: [] })
    })

    const wrapper = mount(ReportPanel, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Execution #99')
    expect(wrapper.text()).toContain('COMPLETED')
    expect(wrapper.find('a[href*="download"]').exists()).toBe(true)
  })
})
