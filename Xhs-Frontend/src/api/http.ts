import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { i18n } from '@/locales'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

const baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

const http = axios.create({
  baseURL,
  timeout: 60000,
})

let refreshing = false
let refreshQueue: Array<(token: string) => void> = []

function processQueue(token: string) {
  refreshQueue.forEach((cb) => cb(token))
  refreshQueue = []
}

function redirectToLogin() {
  clearTokens()
  const loginPath = '/login'
  if (window.location.pathname !== loginPath) {
    window.location.assign(loginPath)
  }
}

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResponse<unknown>
    if (body.code !== 0) {
      const err = new Error(body.message || i18n.global.t('common.requestFailed')) as Error & { code?: number }
      err.code = body.code
      return Promise.reject(err)
    }
    return res
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const body = error.response?.data
    const isAuthEndpoint = original?.url?.includes('/auth/')

    if (body?.code === 40101 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true
      const refresh = getRefreshToken()
      if (!refresh) {
        redirectToLogin()
        return Promise.reject(error)
      }

      if (refreshing) {
        return new Promise((resolve) => {
          refreshQueue.push((token) => {
            original.headers.Authorization = `Bearer ${token}`
            resolve(http(original))
          })
        })
      }

      refreshing = true
      try {
        const res = await axios.post<
          ApiResponse<{ accessToken: string; refreshToken: string; expiresIn: number }>
        >(`${baseURL}/auth/refresh`, { refreshToken: refresh })
        const payload = res.data.data
        if (res.data.code !== 0 || !payload) {
          redirectToLogin()
          return Promise.reject(error)
        }
        setTokens(payload.accessToken, payload.refreshToken)
        processQueue(payload.accessToken)
        original.headers.Authorization = `Bearer ${payload.accessToken}`
        return http(original)
      } catch {
        redirectToLogin()
        return Promise.reject(error)
      } finally {
        refreshing = false
      }
    }

    const message = body?.message || error.message || i18n.global.t('common.requestFailed')
    const err = new Error(message) as Error & { code?: number }
    err.code = body?.code
    return Promise.reject(err)
  },
)

export default http
