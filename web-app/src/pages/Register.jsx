import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'


export default function Register() {
  const { register } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState({ accountNumber: '', holderName: '', phone: '', pin: '', confirmPin: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [submitted, setSubmitted] = useState(null)

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value.replace(/[^\d+]/g, '') }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    if (form.pin !== form.confirmPin) {
      setError('PINs do not match')
      return
    }
    if (!/^\d{4}$/.test(form.pin)) {
      setError('PIN must be exactly 4 digits')
      return
    }
    setSubmitting(true)
    try {
      const res = await register({
        accountNumber: form.accountNumber,
        holderName: form.holderName.trim(),
        phone: form.phone,
        pin: form.pin
      })
      setSubmitted(res)
      toast('Registration submitted for bank approval.', 'success')
    } catch (err) {
      setError(err.data?.error || err.message)
    } finally {
      setSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <div className="auth-layout">
        <div className="auth-card">
          <div className="auth-logo">U</div>
          <div className="modal-check">⏳</div>
          <h1>Application submitted</h1>
          <p className="auth-sub">
            Your account <strong className="mono">{submitted.accountNumber}</strong> has been registered
            and is now <strong>pending bank approval</strong>.
          </p>
          <p className="fine-print" style={{ textAlign: 'left' }}>
            A bank administrator will review your application. Once approved, you will be able to sign in.
            You will need to visit a branch and deposit cash so the administrator can credit your
            mobile-payment balance.
          </p>
          <button className="btn-primary btn-block" style={{ marginTop: '18px' }} onClick={() => navigate('/login')}>
            Go to sign in
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-layout">
      <div className="auth-card">
          <div className="flex items-center justify-between mb-6">
          <div className="auth-logo">U</div>
          <Link to="/" className="text-[12px] text-[var(--faint)] hover:text-[var(--muted)] transition-colors">← Back to home</Link>
        </div>
        <h1>Create account</h1>
        <p className="auth-sub">Your application will be approved by a bank administrator before you can use it.</p>        

        <form onSubmit={handleSubmit}>
          <label>
            Account number
            <input type="text" inputMode="numeric" value={form.accountNumber} onChange={set('accountNumber')} placeholder="3-20 digits" disabled={submitting} required />
          </label>
          <label>
            Full name
            <input type="text" value={form.holderName} onChange={(e) => setForm((f) => ({ ...f, holderName: e.target.value }))} placeholder="e.g. Jordan Lee" disabled={submitting} required />
          </label>
          <label>
            Phone
            <input type="text" inputMode="tel" value={form.phone} onChange={set('phone')} placeholder="+12345678901" disabled={submitting} required />
          </label>
          <label>
            Create 4-digit PIN
            <input type="password" inputMode="numeric" maxLength="4" value={form.pin} onChange={set('pin')} placeholder="••••" disabled={submitting} required />
          </label>
          <label>
            Confirm PIN
            <input type="password" inputMode="numeric" maxLength="4" value={form.confirmPin} onChange={set('confirmPin')} placeholder="••••" disabled={submitting} required />
          </label>

          {error && <div className="form-error">{error}</div>}

          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? (<><span className="spinner" /> Creating…</>) : 'Create account'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}