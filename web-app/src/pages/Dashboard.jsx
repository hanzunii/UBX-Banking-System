import React, { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { accountApi, txnApi } from '../api/client'
import { useRealtime } from '../api/ws'
import { BalanceCardSkeleton, SkeletonList } from '../components/Skeleton'

function formatMoney(balance, currency = 'USD') {
  const fmt = new Intl.NumberFormat(undefined, { style: 'currency', currency })
  return fmt.format(Number(balance || 0))
}

export default function Dashboard() {
  const { user, logout } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [account, setAccount] = useState(null)
  const [loadingAccount, setLoadingAccount] = useState(true)
  const [transactions, setTransactions] = useState([])
  const [loadingTxns, setLoadingTxns] = useState(true)
  const [config, setConfig] = useState(null)

  const refreshAccount = useCallback(async () => {
    try {
      const acc = await accountApi.me()
      setAccount(acc)
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoadingAccount(false)
    }
  }, [toast])

  const refreshTxns = useCallback(async () => {
    try {
      const res = await txnApi.history(10)
      setTransactions(res.transactions || [])
    } catch {
      // keep existing list
    } finally {
      setLoadingTxns(false)
    }
  }, [])

  useEffect(() => {
    refreshAccount()
    refreshTxns()
    window.__systemConfig && setConfig(window.__systemConfig)
  }, [refreshAccount, refreshTxns])

  // Real-time account updates pushed by the gateway over WebSocket
  useRealtime((msg) => {
    if (msg.type === 'ACCOUNT_UPDATED') {
      const isMine = msg.accountNumber === user?.accountNumber
      toast(`${isMine ? 'Your balance changed' : 'Related account'} — ${msg.message} (${formatMoney(msg.balance)})`, isMine ? 'info' : 'success')
      refreshAccount()
      refreshTxns()
    }
  })

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const latestBalance = account?.balance

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-brand"><span className="brand-dot" /> UBX</div>
        <div className="topbar-user">
          <span className="chip">Live</span>
          <div className="user-meta">
            <strong>{user?.holderName}</strong>
            <small>•••• {account?.accountNumber ?? user?.accountNumber}</small>
          </div>
          <button className="btn-ghost" onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      <main className="container">
        <section className="dashboard-grid">
          <div className="col-main">
            {loadingAccount ? (
              <BalanceCardSkeleton />
            ) : (
              <div className="balance-card">
                <div className="balance-head">
                  <span className="label">Available balance</span>
                  <span className={`badge ${config?.maintenanceMode ? 'badge-warn' : 'badge-ok'}`}>
                    {config?.maintenanceMode ? 'Maintenance' : 'Operational'}
                  </span>
                </div>
                <div className="balance-amount">{formatMoney(latestBalance, account?.currency || 'USD')}</div>
                <div className="balance-sub">
                  {account?.holderName} · {account?.accountNumber} · Transfers protected
                </div>
              </div>
            )}

            <div className="actions-grid">
              <Link className="action-card" to="/transfer">
                <span className="action-icon">→</span>
                <strong>Transfer</strong>
                <small>Send money instantly</small>
              </Link>
              <Link className="action-card" to="/receive">
                <span className="action-icon">▪▪</span>
                <strong>Receive</strong>
                <small>Share QR to accept money</small>
              </Link>
              <Link className="action-card" to="/history">
                <span className="action-icon">≣</span>
                <strong>History</strong>
                <small>View transactions</small>
              </Link>
              {(user?.role === 'ADMIN' || user?.role === 'MANAGER') && (
                <Link className="action-card" to="/admin">
                  <span className="action-icon">▲</span>
                  <strong>Admin</strong>
                  <small>Approve &amp; credit</small>
                </Link>
              )}
            </div>
          </div>

          <aside className="col-aside">
            <div className="panel">
              <div className="panel-head">
                <h2>Recent activity</h2>
                <Link to="/history" className="link">View all</Link>
              </div>
              {loadingTxns ? (
                <SkeletonList rows={4} />
              ) : transactions.length === 0 ? (
                <p className="empty">No transactions yet.</p>
              ) : (
                <div className="txn-list">
                  {transactions.map((t) => {
                    const credit = t.type === 'DEPOSIT' || (t.type === 'TRANSFER' && t.fromAccountNumber !== user?.accountNumber)
                    const labels = { TRANSFER: 'Transfer', DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal' }
                    const peer = t.type === 'TRANSFER'
                      ? (t.fromAccountNumber === user?.accountNumber ? `To ${t.toAccountNumber}` : `From ${t.fromAccountNumber}`)
                      : (labels[t.type] || t.type)
                    return (
                      <div className="txn-card" key={t.id}>
                        <div className="txn-main">
                          <strong>{peer}</strong>
                          <small>{new Date(t.timestamp).toLocaleString()}</small>
                        </div>
                        <span className={credit ? 'txn-amt credit' : 'txn-amt debit'}>
                          {credit ? '+' : '−'}{formatMoney(Math.abs(Number(t.amount)))}
                        </span>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </aside>
        </section>
      </main>
    </div>
  )
}