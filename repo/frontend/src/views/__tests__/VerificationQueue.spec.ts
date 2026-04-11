import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import VerificationQueue from '@/views/admin/VerificationQueue.vue'
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
    routes: [{ path: '/', component: VerificationQueue }],
  })
}

const mockQueueItems = [
  {
    type: 'PERSON',
    id: 1,
    accountId: 42,
    status: 'UNDER_REVIEW',
    legalName: 'Jane Doe',
    dobMasked: '****-**-**',
    fileName: null,
    fileSize: null,
    contentType: null,
    duplicateFlag: false,
    createdAt: '2026-04-09T10:00:00Z',
  },
  {
    type: 'ORG_DOCUMENT',
    id: 5,
    accountId: 77,
    status: 'UNDER_REVIEW',
    legalName: null,
    dobMasked: null,
    fileName: 'cert.pdf',
    fileSize: 204800,
    contentType: 'application/pdf',
    duplicateFlag: true,
    createdAt: '2026-04-09T11:00:00Z',
  },
]

describe('VerificationQueue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders person verification items with legal name and masked DOB', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockQueueItems })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Jane Doe')
    expect(wrapper.text()).toContain('****-**-**')
    expect(wrapper.text()).toContain('Account #42')
  })

  it('renders org document items with file info and duplicate flag', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockQueueItems })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('cert.pdf')
    expect(wrapper.text()).toContain('204800 bytes')
    expect(wrapper.text()).toContain('Duplicate flag detected')
    expect(wrapper.text()).toContain('Account #77')
  })

  it('renders View Document link for org documents', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockQueueItems })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    const link = wrapper.find('.view-document-link')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/api/v1/admin/verification/org-document/5/download')
    expect(link.attributes('target')).toBe('_blank')
  })

  it('does not render View Document link for person items', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockQueueItems[0]] })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.find('.view-document-link').exists()).toBe(false)
  })

  it('submits APPROVE decision for a queue item', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockQueueItems[0]] })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/admin/verification/person/1/decision',
      expect.objectContaining({ decision: 'APPROVE' })
    )
    expect(wrapper.text()).toContain('Decision submitted successfully')
  })

  it('shows empty state when queue is empty', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })
    const wrapper = mount(VerificationQueue, { global: { plugins: [createPinia(), createTestRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No pending verifications')
  })
})
