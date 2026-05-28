import http from './http'
import type { ApiResponse } from '@/types/api'
import type { AuthResponse, LoginRequest, RegisterRequest, UserProfile } from '@/types/auth'

export function login(data: LoginRequest) {
  return http.post<ApiResponse<AuthResponse>>('/auth/login', data)
}

export function register(data: RegisterRequest) {
  return http.post<ApiResponse<AuthResponse>>('/auth/register', data)
}

export function refreshToken(refreshToken: string) {
  return http.post<ApiResponse<Pick<AuthResponse, 'accessToken' | 'refreshToken' | 'expiresIn'>>>(
    '/auth/refresh',
    { refreshToken },
  )
}

export function fetchCurrentUser() {
  return http.get<ApiResponse<UserProfile>>('/auth/me')
}
