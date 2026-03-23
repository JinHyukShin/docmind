import React from 'react'

// ─── 범용 Card ────────────────────────────────────────────────────────────────

type CardProps = {
  children: React.ReactNode
  className?: string
  padding?: boolean
}

export function Card({ children, className = '', padding = true }: CardProps) {
  return (
    <div
      className={`rounded-xl border border-gray-200 bg-white shadow-sm ${padding ? 'p-5' : ''} ${className}`}
    >
      {children}
    </div>
  )
}

export function CardHeader({
  children,
  className = '',
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <div className={`border-b border-gray-100 px-5 py-4 ${className}`}>
      {children}
    </div>
  )
}

export function CardBody({
  children,
  className = '',
}: {
  children: React.ReactNode
  className?: string
}) {
  return <div className={`px-5 py-4 ${className}`}>{children}</div>
}

// ─── MetricCard: 수치 표시용 ──────────────────────────────────────────────────

type MetricCardProps = {
  title: string
  value: string | number
  subtitle?: string
  valueColor?: string
  loading?: boolean
  badge?: React.ReactNode
}

export function MetricCard({
  title,
  value,
  subtitle,
  valueColor = 'text-gray-900',
  loading = false,
  badge,
}: MetricCardProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-gray-500">{title}</p>
        {badge}
      </div>
      {loading ? (
        <div className="mt-2 h-9 w-24 animate-pulse rounded bg-gray-200" aria-hidden="true" />
      ) : (
        <p className={`mt-2 text-3xl font-bold tabular-nums ${valueColor}`}>{value}</p>
      )}
      {subtitle && <p className="mt-1 text-xs text-gray-400">{subtitle}</p>}
    </div>
  )
}
