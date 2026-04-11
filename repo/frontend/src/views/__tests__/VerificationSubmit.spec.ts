import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import VerificationSubmit from '@/views/participant/VerificationSubmit.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: VerificationSubmit }],
  })
}

describe('VerificationSubmit', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders person verification form with name and DOB fields', () => {
    const router = createTestRouter()
    const wrapper = mount(VerificationSubmit, {
      global: { plugins: [createPinia(), router] },
    })
    expect(wrapper.find('#legalName').exists()).toBe(true)
    expect(wrapper.find('#dateOfBirth').exists()).toBe(true)
  })

  it('renders org document upload with file input', () => {
    const router = createTestRouter()
    const wrapper = mount(VerificationSubmit, {
      global: { plugins: [createPinia(), router] },
    })
    const fileInput = wrapper.find('input[type="file"]')
    expect(fileInput.exists()).toBe(true)
    expect(fileInput.attributes('accept')).toContain('.pdf')
    expect(fileInput.attributes('accept')).toContain('.jpg')
  })
})
