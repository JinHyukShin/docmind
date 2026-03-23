import React, { useState, useCallback } from 'react'
import type { ApiResponse, SearchResponse, SearchResult, SearchType } from '@/types'
import apiClient from '@/api/client'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Table'

// ─── 검색 타입 선택 ───────────────────────────────────────────────────────────

const SEARCH_TYPES: { value: SearchType; label: string; desc: string }[] = [
  { value: 'full_text', label: '전문 검색', desc: '키워드 기반' },
  { value: 'semantic', label: '시맨틱 검색', desc: 'AI 의미 검색' },
  { value: 'hybrid', label: '하이브리드', desc: '통합 검색 (권장)' },
]

// ─── 검색 결과 카드 ───────────────────────────────────────────────────────────

function SearchResultCard({ result, index }: { result: SearchResult; index: number }) {
  const scorePercent = Math.round(result.score * 100)

  return (
    <Card className="hover:border-sky-300 transition-colors cursor-default">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="shrink-0 text-xs font-bold text-gray-400">#{index + 1}</span>
          <h3 className="text-sm font-semibold text-gray-900 truncate">
            {result.documentTitle}
          </h3>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Badge variant={scorePercent >= 85 ? 'green' : scorePercent >= 70 ? 'yellow' : 'gray'}>
            {scorePercent}%
          </Badge>
          <Badge variant="blue">{result.searchType}</Badge>
        </div>
      </div>

      {/* 하이라이트된 내용 */}
      {result.highlightedContent ? (
        <p
          className="text-sm text-gray-600 leading-relaxed line-clamp-4"
          dangerouslySetInnerHTML={{ __html: result.highlightedContent }}
        />
      ) : (
        <p className="text-sm text-gray-600 leading-relaxed line-clamp-4">
          {result.content}
        </p>
      )}
    </Card>
  )
}

// ─── SearchPage ───────────────────────────────────────────────────────────────

const PAGE_SIZE = 20

function SearchPage() {
  const [query, setQuery] = useState('')
  const [searchType, setSearchType] = useState<SearchType>('hybrid')
  const [results, setResults] = useState<SearchResult[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [searched, setSearched] = useState(false)
  const [lastQuery, setLastQuery] = useState('')
  const [lastType, setLastType] = useState<SearchType>('hybrid')

  const totalPages = Math.ceil(totalCount / PAGE_SIZE)

  const doSearch = useCallback(
    async (q: string, type: SearchType, p: number) => {
      if (!q.trim()) return

      setLoading(true)
      setErrorMsg(null)

      try {
        const res = await apiClient.get<ApiResponse<SearchResponse>>('/search', {
          params: { q: q.trim(), type, page: p, size: PAGE_SIZE },
        })
        const data = res.data.data
        setResults(data.results ?? [])
        setTotalCount(data.totalCount ?? 0)
        setPage(p)
      } catch {
        setErrorMsg('검색에 실패했습니다. 다시 시도해주세요.')
        setResults([])
        setTotalCount(0)
      } finally {
        setLoading(false)
        setSearched(true)
      }
    },
    [],
  )

  const handleSearch = () => {
    if (!query.trim()) return
    setLastQuery(query)
    setLastType(searchType)
    doSearch(query, searchType, 0)
  }

  const handlePageChange = (p: number) => {
    doSearch(lastQuery, lastType, p)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSearch()
  }

  return (
    <div className="space-y-5">
      {/* 검색 헤더 */}
      <div>
        <h2 className="text-xl font-bold text-gray-900">문서 검색</h2>
        <p className="text-sm text-gray-500 mt-0.5">전문 검색, 시맨틱 검색, 하이브리드 검색을 지원합니다.</p>
      </div>

      {/* 검색 영역 */}
      <Card>
        <div className="space-y-4">
          {/* 검색바 */}
          <div className="flex gap-2">
            <div className="relative flex-1">
              <svg
                className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                />
              </svg>
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="검색어를 입력하세요..."
                className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm outline-none focus:border-sky-400 focus:ring-1 focus:ring-sky-400"
                aria-label="검색어 입력"
              />
            </div>
            <button
              type="button"
              onClick={handleSearch}
              disabled={loading || !query.trim()}
              className="flex items-center gap-2 rounded-lg bg-sky-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-700 disabled:opacity-50 transition-colors"
            >
              {loading ? (
                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
              ) : (
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              )}
              검색
            </button>
          </div>

          {/* 검색 타입 선택 */}
          <div className="flex gap-2" role="group" aria-label="검색 타입 선택">
            {SEARCH_TYPES.map((type) => (
              <button
                key={type.value}
                type="button"
                onClick={() => setSearchType(type.value)}
                className={`flex-1 rounded-lg border px-3 py-2 text-xs font-medium transition-colors ${
                  searchType === type.value
                    ? 'border-sky-500 bg-sky-50 text-sky-700'
                    : 'border-gray-200 bg-white text-gray-600 hover:border-sky-300 hover:text-sky-600'
                }`}
                aria-pressed={searchType === type.value}
              >
                <span className="block font-semibold">{type.label}</span>
                <span className="block text-[10px] opacity-70 mt-0.5">{type.desc}</span>
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* 에러 */}
      {errorMsg && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3">
          <p className="text-sm text-red-600">{errorMsg}</p>
        </div>
      )}

      {/* 결과 */}
      {searched && !loading && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <p className="text-sm text-gray-600">
              <span className="font-medium text-gray-900">"{lastQuery}"</span> 검색 결과{' '}
              <span className="font-semibold text-sky-600">{totalCount}건</span>
            </p>
          </div>

          {results.length === 0 ? (
            <Card>
              <div className="py-12 text-center">
                <svg
                  className="mx-auto h-10 w-10 text-gray-300 mb-3"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                  />
                </svg>
                <p className="text-sm text-gray-500">검색 결과가 없습니다.</p>
                <p className="text-xs text-gray-400 mt-1">다른 검색어나 검색 타입을 시도해보세요.</p>
              </div>
            </Card>
          ) : (
            <div className="space-y-3">
              {results.map((result, idx) => (
                <SearchResultCard key={result.chunkId} result={result} index={page * PAGE_SIZE + idx} />
              ))}

              {totalPages > 1 && (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalCount}
                  pageSize={PAGE_SIZE}
                  onPageChange={handlePageChange}
                />
              )}
            </div>
          )}
        </div>
      )}

      {/* 로딩 스켈레톤 */}
      {loading && (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="rounded-xl border border-gray-200 bg-white p-5">
              <div className="h-4 w-48 animate-pulse rounded bg-gray-200 mb-2" />
              <div className="h-3 w-full animate-pulse rounded bg-gray-100 mb-1" />
              <div className="h-3 w-5/6 animate-pulse rounded bg-gray-100" />
            </div>
          ))}
        </div>
      )}

      {/* 초기 상태 */}
      {!searched && !loading && (
        <Card>
          <div className="py-12 text-center">
            <svg
              className="mx-auto h-12 w-12 text-gray-300 mb-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
            <p className="text-sm text-gray-500 mb-1">검색어를 입력하고 검색 버튼을 클릭하세요.</p>
            <p className="text-xs text-gray-400">
              하이브리드 검색은 전문 검색과 의미 검색을 결합하여 더 정확한 결과를 제공합니다.
            </p>
          </div>
        </Card>
      )}
    </div>
  )
}

export default SearchPage
