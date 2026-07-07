import type { ApiResponse } from '@/types/api'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

const baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

let refreshPromise: Promise<string | null> | null = null

interface RefreshPayload {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

function redirectToLogin() {
  clearTokens()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

async function doRefreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    redirectToLogin()
    return null
  }

  try {
    const response = await fetch(`${baseURL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) {
      redirectToLogin()
      return null
    }

    const body = (await response.json()) as ApiResponse<RefreshPayload>
    if (body.code !== 0 || !body.data) {
      redirectToLogin()
      return null
    }

    setTokens(body.data.accessToken, body.data.refreshToken)
    return body.data.accessToken
  } catch {
    redirectToLogin()
    return null
  }
}

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = doRefreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(input, { ...init, headers })
  if (response.status !== 401) {
    return response
  }

  const refreshedToken = await refreshAccessToken()
  if (!refreshedToken) {
    return response
  }

  const retryHeaders = new Headers(init.headers)
  retryHeaders.set('Authorization', `Bearer ${refreshedToken}`)
  return fetch(input, { ...init, headers: retryHeaders })
}
