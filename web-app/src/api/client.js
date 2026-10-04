const API_BASE = import.meta.env.VITE_API_BASE || '/api'

const TOKEN_KEY = 'atm_access_token'
const REFRESH_KEY = 'atm_refresh_token'
const USER_KEY = 'atm_user'

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(access, refresh) {
  if (access) localStorage.setItem(TOKEN_KEY, access)
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
}

export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null
  }
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

function isPublic(path) {
  return (
    path === '/auth/login' ||
    path === '/auth/register' ||
    path === '/auth/refresh' ||
    path.startsWith('/health') ||
    path.startsWith('/system/config')
  )
}

async function refreshAccessToken() {
  const refresh = getRefreshToken()
  if (!refresh) return false
  const res = await fetch(`${API_BASE}/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh })
  })
  if (!res.ok) return false
  const data = await res.json()
  setTokens(data.accessToken, data.refreshToken)
  return true
}

export async function api(path, { method = 'GET', body, idempotencyKey, retry = true } = {}) {
  const headers = {}
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyKey) headers['X-Idempotency-Key'] = idempotencyKey

  const res = await fetch(`${API_BASE}/v1${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })

  if (res.status === 401 && token && !isPublic(path) && retry) {
    const ok = await refreshAccessToken()
    if (ok) return api(path, { method, body, idempotencyKey, retry: false })
    clearAuth()
    window.location.href = '/login'
    throw new Error('Session expired')
  }

  let data = null
  try {
    data = await res.json()
  } catch {
    data = null
  }
  if (!res.ok) {
    const err = new Error(data?.error || `Request failed with ${res.status}`)
    err.status = res.status
    err.data = data
    throw err
  }
  return data
}

export const authApi = {
  login: (accountNumber, pin) => api('/auth/login', { method: 'POST', body: { accountNumber, pin, deviceId: `web-${Math.random().toString(36).slice(2)}` } }),
  verifyMfa: (mfaToken, otp) => api('/auth/mfa/verify', { method: 'POST', body: { mfaToken, otp } }),
  register: (payload) => api('/auth/register', { method: 'POST', body: payload }),
  logout: () => api('/auth/logout', { method: 'POST' }).catch(() => {})
}

export const accountApi = {
  me: () => api('/accounts/me')
}

export const txnApi = {
  transfer: (toAccountNumber, amount, reference) =>
    api('/transactions/transfer', {
      method: 'POST',
      body: { toAccountNumber, amount, reference },
      idempotencyKey: crypto.randomUUID()
    }),
  history: (limit = 20) => api(`/transactions/history?limit=${limit}`)
}

export const systemApi = {
  health: () => api('/health'),
  config: () => api('/system/config')
}

export const adminApi = {
  pending: () => api('/admin/pending'),
  approve: (accountNumber) => api(`/admin/${accountNumber}/approve`, { method: 'POST' }),
  reject: (accountNumber) => api(`/admin/${accountNumber}/reject`, { method: 'POST' }),
  credit: (accountNumber, amount, reference) => api(`/admin/${accountNumber}/credit`, {
    method: 'POST',
    body: { amount, reference },
    idempotencyKey: crypto.randomUUID()
  })
}