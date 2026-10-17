import type { PersonaType } from './api'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface UserProfile {
  id: number
  email: string
  displayName: string | null
  defaultPersona: PersonaType
  dailyQuota: number
}

export interface UserUsage {
  dailyQuota: number
  usedToday: number
  remaining: number
}

export interface UpdateProfileRequest {
  displayName?: string
  defaultPersona?: PersonaType
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserProfile
  workspaces: WorkspaceSummary[]
  defaultWorkspaceId: string | null
}

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'EDITOR' | 'REVIEWER' | 'VIEWER'

export interface WorkspaceSummary {
  id: string
  name: string
  slug?: string
  role: WorkspaceRole
}

export interface WorkspaceMember {
  userId: number
  email: string | null
  displayName: string | null
  role: WorkspaceRole
  status: string
  joinedAt?: string
  lastActiveAt?: string
}

export interface WorkspaceDetail extends WorkspaceSummary {
  industry?: string
  region?: string
  defaultLocale?: string
  defaultQuota?: number
  ownerUserId?: number
  members?: WorkspaceMember[]
}
