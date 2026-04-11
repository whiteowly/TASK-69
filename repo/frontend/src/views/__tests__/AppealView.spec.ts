import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import AppealView from '@/views/AppealView.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: AppealView },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ],
  })
}

describe('AppealView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows blacklisted message', () => {
    const router = createTestRouter()
    const wrapper = mount(AppealView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('h1').text()).toBe('Account Blacklisted')
    expect(wrapper.find('.blacklist-message').text()).toContain(
      'Your account has been blacklisted'
    )
  })

  it('shows loading state on mount (auto-fetches blacklist info)', () => {
    const router = createTestRouter()
    const wrapper = mount(AppealView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.text()).toContain('Loading blacklist information')
  })

  it('has a logout button', () => {
    const router = createTestRouter()
    const wrapper = mount(AppealView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('.logout-btn').exists()).toBe(true)
    expect(wrapper.find('.logout-btn').text()).toBe('Logout')
  })

  it('does not require manual blacklist ID input', () => {
    const router = createTestRouter()
    const wrapper = mount(AppealView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    // No blacklist ID input field — auto-populated from backend
    expect(wrapper.find('#blacklistId').exists()).toBe(false)
  })
})
