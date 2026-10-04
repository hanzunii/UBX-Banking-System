import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { adminApi } from '../api/client'

function formatMoney(balance) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(Number(balance || 0))
}

export default function AdminPanel() {
  const { user } = useAuth()
  const toast = useToast()

  const [pending, setPending] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [creditFor, setCreditFor] = useState(null)
  const [creditAmount, setCreditAmount] = useState('')
  const [creditRef, setCreditRef] = useState('')
  const [denied, setDenied] = useState(false)

  const load = useCallback(async () => {
    try {
      const list = await adminApi.pending()
      setPending(Array.isArray(list) ? list : [])
      setDenied(false)
    } catch (err) {
      if (err.status === 403) {
        setDenied(true)
      } else {
        toast(err.message, 'error')
      }
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => { load() }, [load])

  const approve = async (acct) => {
    setBusy(acct)
    try {
      await adminApi.approve(acct)
      toast(`Account ${acct} approved`, 'success')
      setPending((p) => p.filter((a) => a.accountNumber !== acct))
    } catch (err) {
      toast(err.data?.error || err.message, 'error')
    } finally {
      setBusy(null)
    }
  }

  const reject = async (acct) => {
    setBusy(acct)
    try {
      await adminApi.reject(acct)
      toast(`Account ${acct} rejected`, 'success')
      setPending((p) => p.filter((a) => a.accountNumber !== acct))
    } catch (err) {
      toast(err.data?.error || err.message, 'error')
    } finally {
      setBusy(null)
    }
  }

  const credit = async (e) => {
    e.preventDefault()
    const amt = Number(creditAmount)
    if (!amt || amt <= 0) {
      toast('Enter a valid credit amount', 'error')
      return
    }
    setBusy(creditFor)
    try {
      await adminApi.credit(creditFor, amt.toFixed(2), creditRef.trim())
      toast(`Credited ${formatMoney(amt)} to account ${creditFor}`, 'success')
      setCreditFor(null)
      setCreditAmount('')
      setCreditRef('')
      load()
    } catch (err) {
      toast(err.data?.error || err.message, 'error')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-brand"><span className="brand-dot" /> UBX · Admin</div>
        <div className="topbar-user">
          <span className="chip">Admin</span>
          <div className="user-meta"><strong>{user?.holderName}</strong><small>{user?.accountNumber}</small></div>
          <Link to="/dashboard" className="btn-ghost">← Dashboard</Link>
        </div>
      </header>

      <main className="container">
        {denied ? (
          <div className="panel">
            <div className="form-error">You do not have administrator privileges to view this panel.</div>
            <p style={{ marginTop: '1rem' }}>
              <Link to="/dashboard" className="link">← Back to dashboard</Link>
            </p>
          </div>
        ) : (
          <>
            <section className="panel">
              <div className="panel-head">
                <h2>Pending account approvals</h2>
                <span className="badge badge-warn">{pending.length} waiting</span>
              </div>
              <p className="fine-print">
                New registrations are placed in an approval queue. Approve an account to activate it,
                then credit the cash the customer deposited at the branch so they can use mobile payment.
              </p>

              {loading ? (
                <p className="empty">Loading pending registrations…</p>
              ) : pending.length === 0 ? (
                <p className="empty">No pending registrations. All requests have been processed.</p>
              ) : (
                <div className="table">
                  <div className="table-head">
                    <span>Account</span><span>Name</span><span>Phone</span><span className="col-right">Action</span>
                  </div>
                  {pending.map((a) => (
                    <div className="table-row" key={a.accountNumber}>
                      <span className="mono">{a.accountNumber}</span>
                      <span>{a.holderName}</span>
                      <span>{a.phone || '—'}</span>
                      <span className="col-right">
                        <button className="btn-primary btn-sm" onClick={() => approve(a.accountNumber)} disabled={busy === a.accountNumber}>
                          {busy === a.accountNumber ? '…' : 'Approve'}
                        </button>
                        <button className="btn-ghost btn-sm" onClick={() => reject(a.accountNumber)} disabled={busy === a.accountNumber}>
                          Reject
                        </button>
                        <button className="btn-ghost btn-sm" onClick={() => setCreditFor(a.accountNumber)} disabled={busy === a.accountNumber}>
                          Credit
                        </button>
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="panel">
              <div className="panel-head">
                <h2>Approve & credit workflow</h2>
              </div>
              <p className="fine-print">
                First <strong>Approve</strong> an account to activate it, then use <strong>Credit</strong>
                to record the cash deposit the customer handed over at the branch. The credited funds
                become their mobile-payment balance.
              </p>
              {creditFor ? (
                <form onSubmit={credit} className="credit-form">
                  <p>
                    Crediting account <strong className="mono">{creditFor}</strong>
                  </p>
                  <label>
                    Amount (USD) — cash received at branch
                    <input type="number" inputMode="decimal" min="0.01" step="0.01" value={creditAmount}
                      onChange={(e) => setCreditAmount(e.target.value)} placeholder="0.00" disabled={busy === creditFor} required />
                  </label>
                  <label>
                    Reference (optional)
                    <input type="text" maxLength="100" value={creditRef}
                      onChange={(e) => setCreditRef(e.target.value)} placeholder="e.g. cash deposit MMK 880,000" disabled={busy === creditFor} />
                  </label>
                  <div className="row-buttons">
                    <button type="submit" className="btn-primary" disabled={busy === creditFor}>
                      {busy === creditFor ? (<><span className="spinner" /> Crediting…</>) : 'Credit funds'}
                    </button>
                    <button type="button" className="btn-ghost" onClick={() => { setCreditFor(null); setCreditAmount(''); setCreditRef('') }}>Cancel</button>
                  </div>
                </form>
              ) : (
                <p className="fine-print">Select <strong>Credit</strong> on an approved pending account to open the cash-deposit form.</p>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  )
}
