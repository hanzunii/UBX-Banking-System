import React, { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { accountApi, txnApi, api } from '../api/client'
import { useRealtime } from '../api/ws'

function formatMoney(balance) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(Number(balance || 0))
}

export default function Transfer() {
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [toAccount, setToAccount] = useState('')
  const [amount, setAmount] = useState('')
  const [reference, setReference] = useState('')
  const [balance, setBalance] = useState(null)
  const [processing, setProcessing] = useState(false)
  const [receipt, setReceipt] = useState(null)
  const [error, setError] = useState(null)
  const [recipient, setRecipient] = useState(null)
  const [lookupError, setLookupError] = useState(null)

  useEffect(() => {
    if (!receipt) return
    const t = setTimeout(() => setReceipt(null), 5000)
    return () => clearTimeout(t)
  }, [receipt])

  // Load the current balance on page load so the user knows their available limit
  const loadBalance = async () => {
    try {
      const acc = await accountApi.me()
      setBalance(acc.balance)
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  useEffect(() => {
    loadBalance()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Live balance: keep in sync with real-time pushes
  useRealtime((msg) => {
    if (msg.type === 'ACCOUNT_UPDATED' && msg.accountNumber === user?.accountNumber) {
      setBalance(msg.balance)
      if (!processing) toast(`Balance updated — ${formatMoney(msg.balance)}`, 'success')
    }
  })

  // Look up the exact registered name for the entered recipient account number
  const lookupRecipient = async (acct) => {
    if (!/^\d{3,20}$/.test(acct.trim()) || acct.trim() === user?.accountNumber) {
      setRecipient(null)
      setLookupError(null)
      return
    }
    try {
      const res = await api(`/accounts/lookup/${acct.trim()}`)
      setRecipient(res)
      setLookupError(null)
    } catch (err) {
      setRecipient(null)
      setLookupError(err.data?.error || 'Account not found')
    }
  }

  const handleToAccountChange = (e) => {
    const val = e.target.value
    setToAccount(val)
    clearTimeout(window.__lookupTimer)
    window.__lookupTimer = setTimeout(() => lookupRecipient(val), 400)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    const amt = Number(amount)
    if (!/^\d{3,20}$/.test(toAccount.trim())) {
      setError('Recipient account must be 3-20 digits')
      return
    }
    if (!amt || amt <= 0) {
      setError('Enter a valid amount')
      return
    }
    if (toAccount.trim() === user?.accountNumber) {
      setError('Cannot transfer to your own account')
      return
    }

    setProcessing(true)
    try {
      const res = await txnApi.transfer(toAccount.trim(), amt.toFixed(2), reference.trim().slice(0, 100))
      setReceipt(res)
      toast(`Transfer successful — ${formatMoney(res.transaction.amount)} to ${toAccount}`, 'success')
      await loadBalance()
    } catch (err) {
      setError(err.data?.error || err.message)
    } finally {
      setProcessing(false)
    }
  }

  const closeReceipt = () => {
    setReceipt(null)
    setToAccount('')
    setAmount('')
    setReference('')
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-brand"><span className="brand-dot" /> UBX</div>
        <div className="topbar-user">
          <Link to="/dashboard" className="btn-ghost">← Dashboard</Link>
        </div>
      </header>

      <main className="container narrow">
        <div className="panel">
          <div className="panel-head">
            <h2>Transfer funds</h2>
            <span className="balance-inline">
              Balance: <strong>{balance === null ? '…' : formatMoney(balance)}</strong>
            </span>
          </div>

          <form onSubmit={handleSubmit}>
            <label>
              Recipient account number
              <input
                type="text" inputMode="numeric"
                value={toAccount}
                onChange={handleToAccountChange}
                placeholder="e.g. 10000002"
                disabled={processing}
                required
              />
            </label>
            {recipient && (
              <span className={`recipient-name ${recipient.active && recipient.status === 'ACTIVE' ? '' : 'recipient-inactive'}`}>
                Sending to: <strong>{recipient.holderName}</strong>
                <small className="mono">{recipient.accountNumber}</small>
              </span>
            )}
            {lookupError && <span className="form-error">{lookupError}</span>}
            <label>
              Amount (USD)
              <input
                type="number" inputMode="decimal" min="0.01" step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                disabled={processing}
                required
              />
            </label>
            {balance !== null && (
              <span className="balance-inline">
                Available to send: <strong>{formatMoney(balance)}</strong>
              </span>
            )}
            <label>
              Reference (optional)
              <input
                type="text" maxLength="100"
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                placeholder="e.g. rent for September"
                disabled={processing}
              />
            </label>

            {error && <div className="form-error">{error}</div>}

            <button type="submit" className="btn-primary btn-block" disabled={processing}>
              {processing ? (<><span className="spinner" /> Processing transfer…</>) : 'Confirm transfer'}
            </button>
          </form>
          <p className="fine-print">Transfers are processed atomically with full transaction history.</p>        </div>
      </main>

      {receipt && (
        <div className="modal-overlay" onClick={closeReceipt}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="modal-close" onClick={closeReceipt} aria-label="Close">×</button>
            <div className="modal-check">✓</div>
            <h2>Transfer complete</h2>
            <p>Funds were moved securely between accounts.</p>
            <div className="receipt">
              <div><span>Reference</span><strong>{receipt.transaction.transactionRef}</strong></div>
              <div><span>To account</span><strong>{receipt.transaction.toAccountNumber}</strong></div>
              <div><span>Amount</span><strong>{formatMoney(receipt.transaction.amount)}</strong></div>
              <div><span>New balance</span><strong>{formatMoney(receipt.transaction.senderBalanceAfter)}</strong></div>
            </div>
            <div className="modal-actions">
              <button className="btn-primary" onClick={closeReceipt}>Done</button>
              <button className="btn-ghost" onClick={() => navigate('/history')}>View history</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}