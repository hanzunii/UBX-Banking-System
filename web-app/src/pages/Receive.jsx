import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { accountApi } from '../api/client'
import { generateQR } from '../utils/qrcode'

export default function Receive() {
  const { user } = useAuth()
  const [account, setAccount] = useState(null)
  const [qrSvg, setQrSvg] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    accountApi.me().then((acc) => {
      setAccount(acc)
      // Encode a KPay-style payment payload containing account + name so the sender can transfer.
      const payload = `ATM_BANK|${acc.accountNumber}|${acc.holderName}`
      setQrSvg(generateQR(payload))
    }).catch(() => {})
  }, [])

  const copyAccount = async () => {
    try {
      await navigator.clipboard.writeText(user?.accountNumber || account?.accountNumber || '')
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard unavailable
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

      <main className="container narrow">
        <div className="panel receive-panel">
          <div className="panel-head">
            <h2>Receive money</h2>
          </div>
          <p className="fine-print" style={{ textAlign: 'center', marginBottom: '16px' }}>
            Show this QR code to the sender. They can scan it (or use the details below) to transfer money to you.
          </p>

          {qrSvg ? (
            <div className="qr-box" dangerouslySetInnerHTML={{ __html: qrSvg }} />
          ) : (
            <div className="qr-box qr-loading"><span className="spinner" /></div>
          )}

          <div className="qr-details">
            <div className="qr-row">
              <span>Account holder</span>
              <strong>{account?.holderName || user?.holderName}</strong>
            </div>
            <div className="qr-row">
              <span>Account number</span>
              <strong className="mono">{account?.accountNumber || user?.accountNumber}</strong>
            </div>
          </div>

          <button type="button" className="btn-primary btn-block" onClick={copyAccount} style={{ marginTop: '16px' }}>
            {copied ? 'Copied!' : 'Copy account number'}
          </button>
        </div>
      </main>
    </div>
  )
}
