import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RegisterView from '@/views/RegisterView.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/register', component: RegisterView },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ],
  })
}

describe('RegisterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders registration form', () => {
    const router = createTestRouter()
    const wrapper = mount(RegisterView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('h1').text()).toBe('Create Account')
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('#accountType').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('shows error on empty submit attempt', async () => {
    const router = createTestRouter()
    const wrapper = mount(RegisterView, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-message').text()).toBe('Username and password are required.')
  })
})
