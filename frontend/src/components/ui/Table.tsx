import React from 'react'

// ─── 타입 정의 ────────────────────────────────────────────────────────────────

export type ColumnDef<T> = {
  key: string
  header: string
  render?: (row: T, index: number) => React.ReactNode
  width?: string
  align?: 'left' | 'center' | 'right'
}

type TableProps<T> = {
  columns: ColumnDef<T>[]
  data: T[]
  keyExtractor: (row: T) => string | number
  loading?: boolean
  emptyMessage?: string
  caption?: string
  onRowClick?: (row: T) => void
}

const alignClass = {
  left: 'text-left',
  center: 'text-center',
  right: 'text-right',
}

// ─── 로딩 스켈레톤 ────────────────────────────────────────────────────────────

function SkeletonRows({ cols, rows = 5 }: { cols: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, ri) => (
        <tr key={ri} aria-hidden="true">
          {Array.from({ length: cols }).map((_col, ci) => (
            <td key={ci} className="px-4 py-3">
              <div className="h-4 animate-pulse rounded bg-gray-200" />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}

// ─── Table 컴포넌트 ───────────────────────────────────────────────────────────

export function Table<T>({
  columns,
  data,
  keyExtractor,
  loading = false,
  emptyMessage = '데이터가 없습니다.',
  caption,
  onRowClick,
}: TableProps<T>) {
  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="bg-gray-50">
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  scope="col"
                  className={`px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500 ${alignClass[col.align ?? 'left']} ${col.width ?? ''}`}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
              <SkeletonRows cols={columns.length} />
            ) : data.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-4 py-16 text-center text-sm text-gray-400"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              data.map((row, index) => (
                <tr
                  key={keyExtractor(row)}
                  className={`transition-colors duration-100 ${onRowClick ? 'cursor-pointer hover:bg-sky-50' : 'hover:bg-gray-50'}`}
                  onClick={() => onRowClick?.(row)}
                >
                  {columns.map((col) => (
                    <td
                      key={col.key}
                      className={`px-4 py-3 text-sm text-gray-700 ${alignClass[col.align ?? 'left']} ${col.width ?? ''}`}
                    >
                      {col.render
                        ? col.render(row, index)
                        : String((row as Record<string, unknown>)[col.key] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ─── Pagination ───────────────────────────────────────────────────────────────

type PaginationProps = {
  page: number // 0-based
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (page: number) => void
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
}: PaginationProps) {
  const from = totalElements === 0 ? 0 : page * pageSize + 1
  const to = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div className="flex items-center justify-between px-1 py-3 text-sm text-gray-600">
      <span>
        {totalElements > 0 ? `${from}–${to} / 총 ${totalElements}건` : '0건'}
      </span>
      <div className="flex items-center gap-1">
        <PageButton
          label="처음"
          onClick={() => onPageChange(0)}
          disabled={page === 0}
          aria="처음 페이지"
        />
        <PageButton
          label="이전"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          aria="이전 페이지"
        />
        <span className="px-3 py-1.5 font-medium text-gray-700">
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <PageButton
          label="다음"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria="다음 페이지"
        />
        <PageButton
          label="끝"
          onClick={() => onPageChange(totalPages - 1)}
          disabled={page >= totalPages - 1}
          aria="마지막 페이지"
        />
      </div>
    </div>
  )
}

function PageButton({
  label,
  onClick,
  disabled,
  aria,
}: {
  label: string
  onClick: () => void
  disabled: boolean
  aria: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={aria}
      className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 transition-colors"
    >
      {label}
    </button>
  )
}
