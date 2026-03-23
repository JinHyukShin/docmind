import React, { useState, useCallback } from 'react'
import type { Tag, Document, ApiResponse, PageResponse } from '@/types'
import { useFetch, useApiCall } from '@/hooks/useFetch'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Table, type ColumnDef } from '@/components/ui/Table'
import { StatusBadge } from '@/components/document/StatusBadge'

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

// ─── TagsPage ─────────────────────────────────────────────────────────────────

function TagsPage() {
  const [selectedTagId, setSelectedTagId] = useState<number | null>(null)
  const [selectedTagName, setSelectedTagName] = useState<string>('')
  const [newTagName, setNewTagName] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  // 태그 목록
  const {
    data: tagsData,
    loading: tagsLoading,
    refetch: refetchTags,
  } = useFetch<ApiResponse<Tag[]>>('/tags')

  const tags = tagsData?.data ?? []

  // 태그별 문서 목록
  const { data: tagDocsData, loading: tagDocsLoading } = useFetch<
    ApiResponse<PageResponse<Document>>
  >(
    selectedTagId ? `/tags/${selectedTagId}/documents` : null,
    { params: { page: 0, size: 100 } },
    [selectedTagId],
  )

  const tagDocuments = tagDocsData?.data?.content ?? []

  const deleteTagCall = useApiCall<void, void>('', 'DELETE')

  // 태그 선택
  const handleSelectTag = useCallback((tag: Tag) => {
    setSelectedTagId(tag.id)
    setSelectedTagName(tag.name)
  }, [])

  // 태그 생성
  const handleCreateTag = async () => {
    if (!newTagName.trim()) return
    setCreating(true)
    setCreateError(null)
    try {
      // 태그는 문서 연결 시 생성되지만, 독립 생성 API도 지원
      await fetch('/api/v1/tags', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('docmind_access_token') ?? ''}`,
        },
        body: JSON.stringify({ name: newTagName.trim() }),
      })
      setNewTagName('')
      refetchTags()
    } catch {
      setCreateError('태그 생성에 실패했습니다.')
    } finally {
      setCreating(false)
    }
  }

  // 태그 삭제
  const handleDeleteTag = async (tag: Tag, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm(`"${tag.name}" 태그를 삭제하시겠습니까?`)) return

    await deleteTagCall.execute(undefined, {
      url: `/tags/${tag.id}`,
      method: 'DELETE',
    })

    if (selectedTagId === tag.id) {
      setSelectedTagId(null)
      setSelectedTagName('')
    }
    refetchTags()
  }

  const docColumns: ColumnDef<Document>[] = [
    {
      key: 'title',
      header: '제목',
      render: (row) => <span className="font-medium text-gray-900">{row.title}</span>,
    },
    {
      key: 'originalFileName',
      header: '파일명',
      render: (row) => <span className="text-gray-500 text-xs">{row.originalFileName}</span>,
    },
    {
      key: 'status',
      header: '상태',
      width: 'w-32',
      align: 'center',
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'createdAt',
      header: '생성일',
      width: 'w-32',
      render: (row) => (
        <span className="text-gray-500 text-xs">{formatDate(row.createdAt)}</span>
      ),
    },
  ]

  return (
    <div className="space-y-5">
      {/* 헤더 */}
      <div>
        <h2 className="text-xl font-bold text-gray-900">태그 관리</h2>
        <p className="text-sm text-gray-500 mt-0.5">태그로 문서를 분류하고 빠르게 찾아보세요.</p>
      </div>

      <div className="grid grid-cols-3 gap-5">
        {/* 좌측: 태그 목록 */}
        <div className="col-span-1">
          <Card padding={false}>
            {/* 태그 생성 */}
            <div className="p-4 border-b border-gray-100">
              <p className="text-sm font-semibold text-gray-700 mb-2">태그 추가</p>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={newTagName}
                  onChange={(e) => setNewTagName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleCreateTag()
                  }}
                  placeholder="태그 이름"
                  className="flex-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-sky-400 focus:ring-1 focus:ring-sky-400"
                  aria-label="새 태그 이름"
                />
                <button
                  type="button"
                  onClick={handleCreateTag}
                  disabled={creating || !newTagName.trim()}
                  className="rounded-lg bg-sky-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-sky-700 disabled:opacity-50 transition-colors"
                >
                  추가
                </button>
              </div>
              {createError && (
                <p className="mt-1.5 text-xs text-red-600" role="alert">{createError}</p>
              )}
            </div>

            {/* 태그 목록 */}
            <div className="p-3">
              {tagsLoading ? (
                <div className="space-y-2">
                  {[...Array(6)].map((_, i) => (
                    <div key={i} className="h-8 animate-pulse rounded-lg bg-gray-100" />
                  ))}
                </div>
              ) : tags.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-6">
                  태그가 없습니다.
                </p>
              ) : (
                <ul className="space-y-1" role="list">
                  {tags.map((tag) => (
                    <li key={tag.id}>
                      <button
                        type="button"
                        onClick={() => handleSelectTag(tag)}
                        className={`group w-full flex items-center justify-between rounded-lg px-3 py-2 text-sm transition-colors ${
                          selectedTagId === tag.id
                            ? 'bg-sky-50 border border-sky-200'
                            : 'hover:bg-gray-50 border border-transparent'
                        }`}
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <svg
                            className={`h-3.5 w-3.5 shrink-0 ${
                              selectedTagId === tag.id ? 'text-sky-500' : 'text-gray-400'
                            }`}
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                            aria-hidden="true"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={2}
                              d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
                            />
                          </svg>
                          <span
                            className={`font-medium truncate ${
                              selectedTagId === tag.id ? 'text-sky-700' : 'text-gray-700'
                            }`}
                          >
                            {tag.name}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0">
                          <Badge variant="gray">{tag.documentCount}</Badge>
                          <button
                            type="button"
                            onClick={(e) => handleDeleteTag(tag, e)}
                            className="opacity-0 group-hover:opacity-100 rounded p-0.5 text-gray-400 hover:text-red-500 transition-all"
                            aria-label={`${tag.name} 태그 삭제`}
                          >
                            <svg
                              className="h-3.5 w-3.5"
                              fill="none"
                              stroke="currentColor"
                              viewBox="0 0 24 24"
                              aria-hidden="true"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M6 18L18 6M6 6l12 12"
                              />
                            </svg>
                          </button>
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </Card>
        </div>

        {/* 우측: 태그별 문서 */}
        <div className="col-span-2">
          {selectedTagId === null ? (
            <Card>
              <div className="py-16 text-center">
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
                    d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
                  />
                </svg>
                <p className="text-sm text-gray-500">좌측에서 태그를 선택하면 해당 문서 목록이 표시됩니다.</p>
              </div>
            </Card>
          ) : (
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Badge variant="blue">{selectedTagName}</Badge>
                <span className="text-sm text-gray-500">태그의 문서 목록</span>
              </div>
              <Table
                columns={docColumns}
                data={tagDocuments}
                keyExtractor={(row) => row.id}
                loading={tagDocsLoading}
                emptyMessage="이 태그에 연결된 문서가 없습니다."
                caption={`${selectedTagName} 태그 문서 목록`}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default TagsPage
