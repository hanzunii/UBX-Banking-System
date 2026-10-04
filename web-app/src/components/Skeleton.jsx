import React from 'react'

export function Skeleton({ width = '100%', height = 16, radius = 8, style = {} }) {
  return (
    <div
      className="skeleton"
      style={{ width, height, borderRadius: radius, ...style }}
    />
  )
}

export function BalanceCardSkeleton() {
  return (
    <div className="balance-card">
      <Skeleton width="120" height="18" />
      <Skeleton width="180" height="36" style={{ marginTop: 12 }} />
      <Skeleton width="90" height="14" style={{ marginTop: 10 }} />
    </div>
  )
}

export function SkeletonList({ rows = 4 }) {
  return (
    <div className="txn-list">
      {Array.from({ length: rows }).map((_, i) => (
        <div className="txn-card" key={i}>
          <Skeleton width="140" height="16" />
          <Skeleton width="80" height="16" style={{ marginLeft: 'auto' }} />
        </div>
      ))}
    </div>
  )
}