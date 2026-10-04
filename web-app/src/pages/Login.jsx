import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'


export default function Login() {
  const { login, verifyMfa, user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [accountNumber, setAccountNumber] = useState('')
  const [pin, setPin] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const [mfaPending, setMfaPending] = useState(false)
  const [mfaToken, setMfaToken] = useState('')
  const [demoOtp, setDemoOtp] = useState(null)
  const [otp, setOtp] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const res = await login(accountNumber.trim(), pin)
      if (res && res.requiresOtp) {
        setMfaToken(res.mfaToken)
        setDemoOtp(res.demoOtp)
        setMfaPending(true)
        toast('Enter the 6-digit verification code', 'info')
      } else {
        toast('Signed in successfully', 'success')
        navigate('/dashboard', { replace: true })
      }
    } catch (err) {
      setError(err.data?.error || err.message)
    } finally {
      setSubmitting(false)
    }
  }

  const handleVerify = async (e) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await verifyMfa(mfaToken, otp)
      toast('Signed in successfully', 'success')
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err.data?.error || err.message)
    } finally {
      setSubmitting(false)
    }
  }

  const backToCredentials = () => {
    setMfaPending(false)
    setMfaToken('')
    setDemoOtp(null)
    setOtp('')
    setError(null)
  }

  return (
    <div className="auth-layout">
      <div className="auth-card">
          <div className="flex items-center justify-between mb-6">
          <div className="auth-logo">U</div>
          <Link to="/" className="text-[12px] text-[var(--faint)] hover:text-[var(--muted)] transition-colors">← Back to home</Link>
        </div>
        {!mfaPending ? (
          <>
            <h1>Welcome back</h1>
            <p className="auth-sub">Sign in to your account</p>

            <form onSubmit={handleSubmit}>
              <label>
                Account number
                <input
                  type="text"
                  inputMode="numeric"
                  value={accountNumber}
                  onChange={(e) => setAccountNumber(e.target.value)}
                  placeholder="e.g. 10000001"
                  disabled={submitting}
                  required
                />
              </label>
              <label>
                4-digit PIN
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength="4"
                  value={pin}
                  onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
                  placeholder="••••"
                  disabled={submitting}
                  required
                />
              </label>

              {error && <div className="form-error">{error}</div>}

              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? (
                  <>
                    <span className="spinner" /> Signing in…
                  </>
                ) : (
                  'Sign in'
                )}
              </button>
            </form>

            <p className="auth-footer">
              New here? <Link to="/register">Create an account</Link>
            </p>
            {!user && (
              <p className="demo-hint">
                Demo accounts: <code>10000001</code>/<code>1234</code>,{' '}
                <code>10000002</code>/<code>5678</code>
              </p>
            )}
          </>
        ) : (
          <>
            <h1>Two-factor verification</h1>
            <p className="auth-sub">Enter the 6-digit code sent to your account</p>

            <form onSubmit={handleVerify}>
              <label>
                Verification code
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength="6"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  placeholder="••••••"
                  disabled={submitting}
                  required
                />
              </label>

              {demoOtp && (
                <p className="demo-hint">Demo code for this session: <code>{demoOtp}</code></p>
              )}

              {error && <div className="form-error">{error}</div>}

              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? (
                  <>
                    <span className="spinner" /> Verifying…
                  </>
                ) : (
                  'Verify & sign in'
                )}
              </button>
            </form>

            <p className="auth-footer">
              <button type="button" className="btn-ghost" onClick={backToCredentials}>← Back</button>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
