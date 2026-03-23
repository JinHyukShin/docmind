import React from 'react'

// ─── 범용 뱃지 ────────────────────────────────────────────────────────────────

export type BadgeVariant = 'green' | 'red' | 'yellow' | 'blue' | 'gray' | 'orange' | 'purple'

type BadgeProps = {
  variant?: BadgeVariant
  children: React.ReactNode
  className?: string
}

const variantStyles: Record<BadgeVariant, string> = {
  green: 'bg-green-100 text-green-800 border border-green-200',
  red: 'bg-red-100 text-red-800 border border-red-200',
  yellow: 'bg-yellow-100 text-yellow-800 border border-yellow-200',
  blue: 'bg-blue-100 text-blue-800 border border-blue-200',
  gray: 'bg-gray-100 text-gray-600 border border-gray-200',
  orange: 'bg-orange-100 text-orange-700 border border-orange-200',
  purple: 'bg-purple-100 text-purple-800 border border-purple-200',
}

export function Badge({ variant = 'gray', children, className = '' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${variantStyles[variant]} ${className}`}
    >
      {children}
    </span>
  )
}
