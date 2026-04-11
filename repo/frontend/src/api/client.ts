import axios from 'axios'
import router from '@/router'

const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
})

/**
 * Reads the XSRF-TOKEN cookie set by the backend's CookieCsrfTokenRepository
 * and returns its value so it can be sent as the X-XSRF-TOKEN header.
 */
function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

apiClient.interceptors.request.use((config) => {
  const csrfToken = getCsrfToken()
  if (csrfToken && config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
    config.headers['X-XSRF-TOKEN'] = csrfToken
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const config = error.config || {}

    if (error.response?.status === 401) {
      router.push('/login')
    } else if (error.response?.status === 423 && !config._skipGlobal423) {
      // For in-session blacklisting or lockout detected by the enforcement filter.
      // Login handles its own 423 via _skipGlobal423 flag.
      const code = error.response?.data?.code
      if (code === 'ACCOUNT_BLACKLISTED') {
        router.push('/appeal')
      } else {
        router.push('/locked')
      }
    }
    return Promise.reject(error)
  }
)

export { getCsrfToken }
export default apiClient
