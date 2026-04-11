import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import VolunteerDashboard from '@/views/volunteer/VolunteerDashboard.vue'
import VolunteerVerificationQueue from '@/views/volunteer/VolunteerVerificationQueue.vue'
import VolunteerRegistrationReview from '@/views/volunteer/VolunteerRegistrationReview.vue'
import apiClient from '@/api/client'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  getCsrfToken: vi.fn(() => null),
}))

function createTestRouter(component: any) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component },
      { path: '/workspace/VOLUNTEER', component: VolunteerDashboard },
      { path: '/workspace/VOLUNTEER/verification', component: VolunteerVerificationQueue },
      { path: '/workspace/VOLUNTEER/registrations', component: VolunteerRegistrationReview },
    ],
  })
}

describe('VolunteerDashboard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('displays pending counts', async () => {
    ;(apiClient.get as any).mockImplementation((url: string) => {
      if (url.includes('verification')) return Promise.resolve({ data: [{ id: 1 }, { id: 2 }] })
      if (url.includes('registrations')) return Promise.resolve({ data: [{ id: 3 }] })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(VolunteerDashboard, {
      global: { plugins: [createPinia(), createTestRouter(VolunteerDashboard)] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('Review Queue')
  })
})

describe('VolunteerVerificationQueue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders verification queue items', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, type: 'PERSON', accountId: 10, status: 'UNDER_REVIEW', legalName: 'Jane Doe', createdAt: '2026-01-01' }],
    })
    const wrapper = mount(VolunteerVerificationQueue, {
      global: { plugins: [createPinia(), createTestRouter(VolunteerVerificationQueue)] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('PERSON')
    expect(wrapper.text()).toContain('Jane Doe')
  })
})

describe('VolunteerRegistrationReview', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders pending registrations', async () => {
    ;(apiClient.get as any).mockResolvedValue({
      data: [{ id: 1, eventId: 5, eventTitle: 'First Aid Night', accountId: 10, status: 'PENDING_REVIEW', createdAt: '2026-01-01' }],
    })
    const wrapper = mount(VolunteerRegistrationReview, {
      global: { plugins: [createPinia(), createTestRouter(VolunteerRegistrationReview)] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('First Aid Night')
    expect(wrapper.text()).toContain('Approve')
    expect(wrapper.text()).toContain('Deny')
  })
})
