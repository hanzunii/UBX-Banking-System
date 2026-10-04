import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { txnApi } from '../api/client'
import { useRealtime } from '../api/ws'
import { SkeletonList } from '../components/Skeleton'

function formatMoney(balance) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(Number(balance || 0))
}

export default function History() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadPage = useCallback(async (p, append) => {
    if (p === 1) setLoading(true); else setLoadingMore(true)
    try {
      const res = await txnApi.history(p * 20)
      const list = res.transactions || []
      setTransactions((prev) => (append ? [...prev, ...list] : list))
      setHasMore(list.length === 20)
      setPage(p)
    } catch {
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    loadPage(1, false)
  }, [loadPage])

  useRealtime((msg) => {
    if (msg.type === 'ACCOUNT_UPDATED') {
      loadPage(1, false)
    }
  })

  const handleScroll = (e) => {
    const el = e.currentTarget
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60 && hasMore && !loadingMore) {
      loadPage(page + 1, true)
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-brand"><span className="brand-dot" /> UBX</div>
        <div className="topbar-user">
          <Link to="/dashboard" className="btn-ghost">← Dashboard</Link>
        </div>
      </header>

      <main className="container">
        <div className="panel">
          <div className="panel-head">
            <h2>Transaction history</h2>
          </div>

          {loading ? (
            <SkeletonList rows={6} />
          ) : (
            <div className="txn-list scrollable" onScroll={handleScroll}>
              {transactions.length === 0 && <p className="empty">No transactions yet.</p>}
              {transactions.map((t) => {
                const credit = t.type === 'DEPOSIT' || (t.type === 'TRANSFER' && t.fromAccountNumber !== user?.accountNumber)
                const labels = { TRANSFER: 'Transfer', DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal' }
                let peer = labels[t.type] || t.type
                if (t.type === 'TRANSFER') {
                  peer = t.fromAccountNumber === user?.accountNumber
                    ? `To ${t.toAccountNumber}`
                    : `From ${t.fromAccountNumber}`
                }
                return (
                  <div className="txn-card" key={t.id || t.transactionRef}>
                    <div className="txn-main">
                      <strong>{peer}</strong>
                      <small>{new Date(t.timestamp).toLocaleString()}</small>
                      {t.reference && <small className="txn-ref">{t.reference}</small>}
                    </div>
                    <div className="txn-right">
                      <span className={credit ? 'txn-amt credit' : 'txn-amt debit'}>
                        {credit ? '+' : '−'}{formatMoney(Math.abs(Number(t.amount)))}
                      </span>
                      <small className="txn-balance">Balance {formatMoney(t.senderBalanceAfter)}</small>
                    </div>
                  </div>
                )
              })}
              {loadingMore && <div className="list-loader"><span className="spinner" /> Loading more…</div>}
              {!hasMore && transactions.length > 0 && <p className="empty">End of history.</p>}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}