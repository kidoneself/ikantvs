import { http } from './http'

export interface AppUser {
  id: number
  username: string
  nickname: string
  role?: string
}

export interface AuthResult {
  token: string
  user: AppUser
}

export function login(username: string, password: string) {
  return http.post<AuthResult>('/auth/login', { username, password })
}

export function me() {
  return http.get<AppUser>('/auth/me')
}
