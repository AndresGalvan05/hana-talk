import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, tokenStore, UNAUTHORIZED_EVENT } from '../api/client'
import type { AuthResponse } from '../api/types'

interface AuthState {
  username: string | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(() =>
    tokenStore.get() ? tokenStore.getUsername() : null,
  )

  const logout = useCallback(() => {
    tokenStore.clear()
    setUsername(null)
  }, [])

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<AuthResponse>('/api/auth/login', { email, password })
    tokenStore.set(res.token, res.username)
    setUsername(res.username)
  }, [])

  const register = useCallback(async (email: string, name: string, password: string) => {
    const res = await api.post<AuthResponse>('/api/auth/register', {
      email,
      username: name,
      password,
    })
    tokenStore.set(res.token, res.username)
    setUsername(res.username)
  }, [])

  return (
    <AuthContext.Provider
      value={{ username, isAuthenticated: username !== null, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
