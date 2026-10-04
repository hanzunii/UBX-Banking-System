import { createContext, useContext, useEffect, useState } from 'react'
import { authApi, clearAuth, getUser, setTokens, setUser } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(getUser())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Cold-start: ping health + config until gateway responds (splash/skeleton state)
    let cancelled = false
    let attempts = 0

    const bootstrap = async () => {
      if (cancelled) return
      try {
        const [health, config] = await Promise.all([
          fetch('/api/v1/health').then((r) => r.json()),
          fetch('/api/v1/system/config').then((r) => r.json())
        ])
        if (!cancelled) {
          window.__systemConfig = config
          window.__health = health
          setLoading(false)
        }
      } catch {
        attempts += 1
        if (cancelled) return
        if (attempts >= 20) {
          setLoading(false)
          return
        }
        setTimeout(bootstrap, Math.min(1000 * attempts, 5000))
      }
    }
    bootstrap()
    return () => { cancelled = true }
  }, [])

  const login = async (accountNumber, pin) => {
    const challenge = await authApi.login(accountNumber, pin)
    if (challenge.requiresOtp) {
      return { requiresOtp: true, mfaToken: challenge.mfaToken, demoOtp: challenge.demoOtp }
    }
    setTokens(challenge.accessToken, challenge.refreshToken)
    const me = { accountNumber: challenge.accountNumber, holderName: challenge.holderName, role: challenge.role, currency: challenge.currency }
    setUser(me)
    setUserState(me)
    return me
  }

  const verifyMfa = async (mfaToken, otp) => {
    const res = await authApi.verifyMfa(mfaToken, otp)
    setTokens(res.accessToken, res.refreshToken)
    const me = { accountNumber: res.accountNumber, holderName: res.holderName, role: res.role, currency: res.currency }
    setUser(me)
    setUserState(me)
    return me
  }

  const register = async (payload) => authApi.register(payload)

  const logout = async () => {
    await authApi.logout().catch(() => {})
    clearAuth()
    setUserState(null)
    window.location.href = '/login'
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, verifyMfa, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}