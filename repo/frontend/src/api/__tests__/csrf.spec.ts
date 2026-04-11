import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { getCsrfToken } from '@/api/client'
import axios from 'axios'

describe('CSRF token handling', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('getCsrfToken returns null when no XSRF-TOKEN cookie exists', () => {
    expect(getCsrfToken()).toBeNull()
  })

  it('getCsrfToken reads the XSRF-TOKEN cookie value', () => {
    document.cookie = 'XSRF-TOKEN=abc-123-real-token; path=/'
    expect(getCsrfToken()).toBe('abc-123-real-token')
  })

  it('getCsrfToken handles URL-encoded cookie values', () => {
    document.cookie = 'XSRF-TOKEN=token%3Dwith%26special; path=/'
    expect(getCsrfToken()).toBe('token=with&special')
  })

  it('getCsrfToken ignores other cookies', () => {
    document.cookie = 'JSESSIONID=session123; path=/'
    document.cookie = 'XSRF-TOKEN=the-csrf-token; path=/'
    document.cookie = 'other=value; path=/'
    expect(getCsrfToken()).toBe('the-csrf-token')
  })

  it('axios request interceptor adds X-XSRF-TOKEN header on POST when cookie exists', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value-from-server; path=/'

    // Create a standalone axios instance to test the interceptor pattern in isolation
    const testClient = axios.create({ baseURL: '/api/v1' })

    let capturedHeaders: Record<string, string> = {}

    // Add the same interceptor logic used in client.ts
    testClient.interceptors.request.use((config) => {
      const token = getCsrfToken()
      if (token && config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
        config.headers['X-XSRF-TOKEN'] = token
      }
      // Capture headers then abort before network
      capturedHeaders = { ...config.headers } as Record<string, string>
      throw new axios.Cancel('test-abort')
    })

    try {
      await testClient.post('/test', {})
    } catch {
      // expected
    }

    expect(capturedHeaders['X-XSRF-TOKEN']).toBe('csrf-value-from-server')
  })

  it('axios request interceptor does not add X-XSRF-TOKEN on GET', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value-from-server; path=/'

    const testClient = axios.create({ baseURL: '/api/v1' })
    let capturedHeaders: Record<string, string> = {}

    testClient.interceptors.request.use((config) => {
      const token = getCsrfToken()
      if (token && config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
        config.headers['X-XSRF-TOKEN'] = token
      }
      capturedHeaders = { ...config.headers } as Record<string, string>
      throw new axios.Cancel('test-abort')
    })

    try {
      await testClient.get('/test')
    } catch {
      // expected
    }

    expect(capturedHeaders['X-XSRF-TOKEN']).toBeUndefined()
  })
})
