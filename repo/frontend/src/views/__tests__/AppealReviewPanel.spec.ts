import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import AppealReviewPanel from '@/views/admin/AppealReviewPanel.vue'
import apiClient from '@/api/client'

// Mock apiClient methods
vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  getCsrfToken: vi.fn(() => null),
}))

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: AppealReviewPanel }],
  })
}

const mockAppeals = [
  {
    appealId: 10,
    blacklistRecordId: 5,
    accountId: 42,
    appealText: 'I believe this was a misunderstanding.',
    contactNote: 'Available Mon-Fri at desk',
    status: 'PENDING',
    dueDate: '2026-04-14',
    createdAt: '2026-04-09T12:00:00Z',
  },
  {
    appealId: 11,
    blacklistRecordId: 6,
    accountId: 99,
    appealText: 'Please review my case.',
    contactNote: null,
    status: 'PENDING',
    dueDate: '2026-04-15',
    createdAt: '2026-04-10T08:00:00Z',
  },
]

describe('AppealReviewPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders appeal data from backend including accountId and appealText', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockAppeals })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    // Verify both appeals rendered with correct data
    expect(wrapper.text()).toContain('Appeal #10')
    expect(wrapper.text()).toContain('Account ID: 42')
    expect(wrapper.text()).toContain('I believe this was a misunderstanding.')
    expect(wrapper.text()).toContain('Available Mon-Fri at desk')
    expect(wrapper.text()).toContain('2026-04-14')

    expect(wrapper.text()).toContain('Appeal #11')
    expect(wrapper.text()).toContain('Account ID: 99')
    expect(wrapper.text()).toContain('Please review my case.')
  })

  it('decision dropdown uses APPROVE_UNBLOCK and DENY values', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: mockAppeals })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    const options = wrapper.findAll('option')
    const values = options.map(o => o.attributes('value'))
    expect(values).toContain('APPROVE_UNBLOCK')
    expect(values).toContain('DENY')
    expect(values).not.toContain('APPROVED')
    expect(values).not.toContain('DENIED')
  })

  it('submits decision with correct APPROVE_UNBLOCK value', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockAppeals[0]] })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    // Submit the decision form (default is APPROVE_UNBLOCK)
    const form = wrapper.find('.decision-form')
    await form.trigger('submit')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/admin/appeals/10/decision',
      { decision: 'APPROVE_UNBLOCK', decisionNote: undefined }
    )
  })

  it('shows success message after successful decision', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockAppeals[0]] })
    ;(apiClient.post as any).mockResolvedValue({ data: {} })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    await wrapper.find('.decision-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('approved (unblocked)')
  })

  it('shows error on failed decision submission', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [mockAppeals[0]] })
    ;(apiClient.post as any).mockRejectedValue({
      response: { data: { message: 'Appeal not in PENDING status' } },
    })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    await wrapper.find('.decision-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Appeal not in PENDING status')
  })

  it('shows empty state when no pending appeals', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: [] })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('No pending appeals')
  })

  it('shows error state when API fetch fails', async () => {
    ;(apiClient.get as any).mockRejectedValue({
      response: { data: { message: 'Forbidden' } },
    })

    const router = createTestRouter()
    const wrapper = mount(AppealReviewPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Forbidden')
  })
})
