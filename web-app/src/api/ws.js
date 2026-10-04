import { useEffect, useRef } from 'react'
import { getAccessToken } from '../api/client'

const WS_BASE = (import.meta.env.VITE_WS_BASE || (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host).replace(/\/$/, '')

/**
 * Establishes a resilient WebSocket connection to the gateway and routes
 * server-pushed ACCOUNT_UPDATED events through the onEvent callback.
 * Auto-reconnects with exponential backoff when the socket drops.
 */
export function useRealtime(onEvent) {
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent

  useEffect(() => {
    let ws = null
    let closed = false
    let retryDelay = 1000

    const connect = () => {
      if (closed) return
      ws = new WebSocket(`${WS_BASE}/ws`)

      ws.onopen = () => {
        retryDelay = 1000
        const token = getAccessToken()
        if (token) ws.send(JSON.stringify({ type: 'AUTH', token }))
      }

      ws.onmessage = (evt) => {
        try {
          const msg = JSON.parse(evt.data)
          if (msg.type === 'ACCOUNT_UPDATED') {
            onEventRef.current?.(msg)
          } else if (msg.type === 'AUTH_OK') {
            onEventRef.current?.({ type: 'WS_CONNECTED' })
          }
        } catch {
          // ignore malformed frames
        }
      }

      ws.onclose = () => {
        if (closed) return
        setTimeout(connect, retryDelay)
        retryDelay = Math.min(retryDelay * 2, 30000)
      }

      ws.onerror = () => ws.close()
    }

    connect()
    return () => {
      closed = true
      if (ws) ws.close()
    }
  }, [])
}