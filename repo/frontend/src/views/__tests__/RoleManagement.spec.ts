import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RoleManagement from '@/views/participant/RoleManagement.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: RoleManagement }],
  })
}

describe('RoleManagement', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders role request form with role selector', () => {
    const router = createTestRouter()
    const wrapper = mount(RoleManagement, {
      global: { plugins: [createPinia(), router] },
    })
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
    const options = wrapper.findAll('option')
    const values = options.map(o => o.attributes('value')).filter(Boolean)
    expect(values).toContain('VOLUNTEER')
  })

  it('renders current roles section', () => {
    const router = createTestRouter()
    const wrapper = mount(RoleManagement, {
      global: { plugins: [createPinia(), router] },
    })
    expect(wrapper.text()).toContain('My Roles')
  })
})
