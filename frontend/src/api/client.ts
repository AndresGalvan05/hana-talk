const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'hanatalk.token'
const USERNAME_KEY = 'hanatalk.username'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  getUsername: () => localStorage.getItem(USERNAME_KEY),
  set: (token: string, username: string) => {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USERNAME_KEY, username)
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USERNAME_KEY)
  },
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** Fired when the API rejects our token; AuthContext listens and logs out. */
export const UNAUTHORIZED_EVENT = 'hanatalk:unauthorized'

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_URL}${path}`, { ...options, headers })

  if (response.status === 401 && token) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      if (typeof body?.message === 'string' && body.message) message = body.message
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
}
