import React, { useState, useCallback } from 'react'
import type { Document, DocumentDetail, ApiResponse, PageResponse } from '@/types'
import { useFetch, useApiCall } from '@/hooks/useFetch'
import { Table, type ColumnDef } from '@/components/ui/Table'
import { Modal } from '@/components/ui/Modal'
import { StatusBadge } from '@/components/document/StatusBadge'
import { FileUpload } from '@/components/document/FileUpload'
import { Badge } from '@/components/ui/Badge'
import apiClient from '@/api/client'

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ─── 업로드 모달 ──────────────────────────────────────────────────────────────

type UploadModalProps = {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

function UploadModal({ open, onClose, onSuccess }: UploadModalProps) {
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [uploading, setUploading] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const handleClose = () => {
    setFile(null)
    setTitle('')
    setDescription('')
    setErrorMsg(null)
    onClose()
  }

  const handleSubmit = async () => {
    if (!file) {
      setErrorMsg('파일을 선택해주세요.')
      return
    }
    if (!title.trim()) {
      setErrorMsg('제목을 입력해주세요.')
      return
    }

    setUploading(true)
    setErrorMsg(null)

    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('title', title.trim())
      if (description.trim()) {
        formData.append('description', description.trim())
      }

      await apiClient.post('/documents', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      handleClose()
      onSuccess()
    } catch {
      setErrorMsg('업로드에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="문서 업로드"
      size="md"
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            disabled={uploading}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={uploading || !file || !title.trim()}
            className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700 disabled:opacity-50 transition-colors flex items-center gap-2"
          >
            {uploading && (
              <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
              </svg>
            )}
            업로드
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <FileUpload onFileSelect={setFile} />

        {file && (
          <div className="flex items-center gap-2 rounded-lg bg-sky-50 px-3 py-2 text-sm">
            <svg className="h-4 w-4 text-sky-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span className="text-sky-700 truncate">{file.name}</span>
            <span className="text-sky-500 shrink-0">({formatBytes(file.size)})</span>
          </div>
        )}

        <div>
          <label htmlFor="doc-title" className="block text-sm font-medium text-gray-700 mb-1">
            제목 <span className="text-red-500">*</span>
          </label>
          <input
            id="doc-title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="문서 제목을 입력하세요"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-sky-400 focus:ring-1 focus:ring-sky-400"
          />
        </div>

        <div>
          <label htmlFor="doc-desc" className="block text-sm font-medium text-gray-700 mb-1">
            설명 (선택)
          </label>
          <textarea
            id="doc-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="문서에 대한 간략한 설명을 입력하세요"
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-sky-400 focus:ring-1 focus:ring-sky-400 resize-none"
          />
        </div>

        {errorMsg && (
          <p className="text-sm text-red-600" role="alert">{errorMsg}</p>
        )}
      </div>
    </Modal>
  )
}

// ─── 상세 모달 ────────────────────────────────────────────────────────────────

type DetailModalProps = {
  documentId: number | null
  onClose: () => void
}

function DetailModal({ documentId, onClose }: DetailModalProps) {
  const [generatingSummary, setGeneratingSummary] = useState(false)
  const [summaryError, setSummaryError] = useState<string | null>(null)

  const { data, loading, refetch } = useFetch<ApiResponse<DocumentDetail>>(
    documentId ? `/documents/${documentId}` : null,
  )

  const detail = data?.data ?? null

  const handleGenerateSummary = async () => {
    if (!documentId) return
    setGeneratingSummary(true)
    setSummaryError(null)
    try {
      await apiClient.post(`/documents/${documentId}/summary`)
      refetch()
    } catch {
      setSummaryError('요약 생성에 실패했습니다.')
    } finally {
      setGeneratingSummary(false)
    }
  }

  return (
    <Modal
      open={documentId !== null}
      onClose={onClose}
      title="문서 상세"
      size="lg"
    >
      {loading ? (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-5 animate-pulse rounded bg-gray-200" />
          ))}
        </div>
      ) : detail ? (
        <div className="space-y-5">
          {/* 메타데이터 */}
          <div className="grid grid-cols-2 gap-3">
            <MetaItem label="제목" value={detail.title} />
            <MetaItem label="파일명" value={detail.originalFileName} />
            <MetaItem label="크기" value={formatBytes(detail.fileSize)} />
            <MetaItem label="타입" value={detail.mimeType} />
            <MetaItem label="상태" value={<StatusBadge status={detail.status} />} />
            <MetaItem label="생성일" value={formatDate(detail.createdAt)} />
            {detail.pageCount !== null && (
              <MetaItem label="페이지 수" value={`${detail.pageCount} 페이지`} />
            )}
            {detail.chunkCount !== null && (
              <MetaItem label="청크 수" value={`${detail.chunkCount} 개`} />
            )}
          </div>

          {/* 태그 */}
          {detail.tags && detail.tags.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">태그</p>
              <div className="flex flex-wrap gap-1.5">
                {detail.tags.map((tag) => (
                  <Badge key={tag} variant="blue">{tag}</Badge>
                ))}
              </div>
            </div>
          )}

          {/* 오류 메시지 */}
          {detail.errorMessage && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3">
              <p className="text-xs font-semibold text-red-700 mb-1">처리 오류</p>
              <p className="text-sm text-red-600">{detail.errorMessage}</p>
            </div>
          )}

          {/* AI 요약 */}
          <div className="rounded-xl border border-gray-200 bg-gray-50 p-4">
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm font-semibold text-gray-700">AI 요약</p>
              {detail.status === 'READY' && (
                <button
                  type="button"
                  onClick={handleGenerateSummary}
                  disabled={generatingSummary}
                  className="flex items-center gap-1.5 rounded-lg bg-sky-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-sky-700 disabled:opacity-50 transition-colors"
                >
                  {generatingSummary ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                      </svg>
                      생성 중...
                    </>
                  ) : (
                    <>
                      <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      {detail.summary ? '재생성' : '요약 생성'}
                    </>
                  )}
                </button>
              )}
            </div>

            {summaryError && (
              <p className="text-sm text-red-600 mb-2">{summaryError}</p>
            )}

            {detail.summary ? (
              <div>
                <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                  {detail.summary.content}
                </p>
                <p className="mt-2 text-xs text-gray-400">
                  생성: {formatDate(detail.summary.generatedAt)}
                </p>
              </div>
            ) : (
              <p className="text-sm text-gray-400">
                {detail.status === 'READY'
                  ? '"요약 생성" 버튼을 클릭하여 AI 요약을 생성하세요.'
                  : '문서 처리가 완료된 후 요약을 생성할 수 있습니다.'}
              </p>
            )}
          </div>
        </div>
      ) : (
        <p className="text-sm text-gray-500">문서를 불러올 수 없습니다.</p>
      )}
    </Modal>
  )
}

function MetaItem({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{label}</p>
      <div className="mt-0.5 text-sm text-gray-800">{value}</div>
    </div>
  )
}

// ─── DocumentsPage ────────────────────────────────────────────────────────────

function DocumentsPage() {
  const [page, setPage] = useState(0)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, loading, refetch } = useFetch<ApiResponse<PageResponse<Document>>>(
    '/documents',
    { params: { page, size: 20 } },
    [page],
  )

  const deleteCall = useApiCall<void, void>('', 'DELETE')

  const pageData = data?.data

  const handleDelete = useCallback(
    async (doc: Document, e: React.MouseEvent) => {
      e.stopPropagation()
      if (!confirm(`"${doc.title}" 문서를 삭제하시겠습니까?`)) return
      await deleteCall.execute(undefined, { url: `/documents/${doc.id}`, method: 'DELETE' })
      refetch()
    },
    [deleteCall, refetch],
  )

  const columns: ColumnDef<Document>[] = [
    {
      key: 'title',
      header: '제목',
      render: (row) => (
        <span className="font-medium text-gray-900 hover:text-sky-600 transition-colors">
          {row.title}
        </span>
      ),
    },
    {
      key: 'originalFileName',
      header: '파일명',
      render: (row) => (
        <span className="text-gray-500 text-xs">{row.originalFileName}</span>
      ),
    },
    {
      key: 'status',
      header: '상태',
      width: 'w-32',
      align: 'center',
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'fileSize',
      header: '크기',
      width: 'w-24',
      align: 'right',
      render: (row) => formatBytes(row.fileSize),
    },
    {
      key: 'createdAt',
      header: '생성일',
      width: 'w-40',
      render: (row) => (
        <span className="text-gray-500 text-xs">{formatDate(row.createdAt)}</span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: 'w-12',
      align: 'center',
      render: (row) => (
        <button
          type="button"
          onClick={(e) => handleDelete(row, e)}
          className="rounded p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
          aria-label={`${row.title} 삭제`}
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      ),
    },
  ]

  return (
    <div className="space-y-5">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">문서 목록</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {pageData ? `총 ${pageData.totalElements}개` : '로딩 중...'}
          </p>
        </div>
        <button
          type="button"
          onClick={() => setUploadOpen(true)}
          className="flex items-center gap-2 rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700 transition-colors"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          문서 업로드
        </button>
      </div>

      {/* 테이블 */}
      <Table
        columns={columns}
        data={pageData?.content ?? []}
        keyExtractor={(row) => row.id}
        loading={loading}
        emptyMessage="업로드된 문서가 없습니다. 문서를 업로드해주세요."
        caption="문서 목록"
        onRowClick={(row) => setDetailId(row.id)}
      />

      {/* 페이징 */}
      {pageData && pageData.totalPages > 1 && (
        <div className="flex justify-end">
          <div className="flex items-center gap-1">
            {Array.from({ length: pageData.totalPages }, (_, i) => (
              <button
                key={i}
                type="button"
                onClick={() => setPage(i)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  i === page
                    ? 'bg-sky-600 text-white'
                    : 'border border-gray-300 text-gray-600 hover:bg-gray-50'
                }`}
              >
                {i + 1}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 업로드 모달 */}
      <UploadModal
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onSuccess={() => refetch()}
      />

      {/* 상세 모달 */}
      <DetailModal
        documentId={detailId}
        onClose={() => setDetailId(null)}
      />
    </div>
  )
}

export default DocumentsPage
