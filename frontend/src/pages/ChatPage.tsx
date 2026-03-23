import { useState, useEffect, useRef, useCallback } from 'react'
import type {
  ChatSession,
  ChatMessage as ChatMessageType,
  Document,
  ApiResponse,
  PageResponse,
  SourceChunk,
} from '@/types'
import { useFetch } from '@/hooks/useFetch'
import { useStreamingChat } from '@/hooks/useSSE'
import { ChatMessage } from '@/components/chat/ChatMessage'
import { ChatInput } from '@/components/chat/ChatInput'
import { Modal } from '@/components/ui/Modal'
import apiClient from '@/api/client'

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ─── 새 대화 다이얼로그 ───────────────────────────────────────────────────────

type NewSessionDialogProps = {
  open: boolean
  onClose: () => void
  onConfirm: (title: string, documentIds: number[]) => void
  creating: boolean
}

function NewSessionDialog({ open, onClose, onConfirm, creating }: NewSessionDialogProps) {
  const [title, setTitle] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const { data } = useFetch<ApiResponse<PageResponse<Document>>>(
    open ? '/documents' : null,
    { params: { page: 0, size: 100, status: 'READY' } },
  )

  const documents = data?.data?.content ?? []

  const handleToggle = (id: number) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const handleConfirm = () => {
    if (!title.trim()) return
    onConfirm(title.trim(), selectedIds)
  }

  const handleClose = () => {
    setTitle('')
    setSelectedIds([])
    onClose()
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="새 대화 시작"
      size="md"
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            disabled={creating}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={creating || !title.trim()}
            className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700 disabled:opacity-50 transition-colors flex items-center gap-2"
          >
            {creating && (
              <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
              </svg>
            )}
            대화 시작
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label htmlFor="session-title" className="block text-sm font-medium text-gray-700 mb-1">
            대화 제목 <span className="text-red-500">*</span>
          </label>
          <input
            id="session-title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: Spring Boot 질문"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-sky-400 focus:ring-1 focus:ring-sky-400"
          />
        </div>

        <div>
          <p className="text-sm font-medium text-gray-700 mb-2">
            참조할 문서 선택 (선택 없으면 전체 문서 대상)
          </p>
          {documents.length === 0 ? (
            <p className="text-sm text-gray-400 py-3 text-center">준비된 문서가 없습니다.</p>
          ) : (
            <div className="max-h-48 overflow-y-auto space-y-1 rounded-lg border border-gray-200 p-2">
              {documents.map((doc) => (
                <label
                  key={doc.id}
                  className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-gray-50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(doc.id)}
                    onChange={() => handleToggle(doc.id)}
                    className="rounded border-gray-300 text-sky-600 focus:ring-sky-400"
                  />
                  <span className="text-sm text-gray-700 truncate">{doc.title}</span>
                </label>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}

// ─── ChatPage ─────────────────────────────────────────────────────────────────

function ChatPage() {
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMessageType[]>([])
  const [streamingContent, setStreamingContent] = useState('')
  const [streamingSources, setStreamingSources] = useState<SourceChunk[]>([])
  const [inputValue, setInputValue] = useState('')
  const [newSessionOpen, setNewSessionOpen] = useState(false)
  const [creatingSession, setCreatingSession] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { status: sseStatus, start: startStream, abort: abortStream } = useStreamingChat()

  const isStreaming = sseStatus === 'streaming' || sseStatus === 'connecting'

  // 세션 목록
  const {
    data: sessionsData,
    loading: sessionsLoading,
    refetch: refetchSessions,
  } = useFetch<ApiResponse<PageResponse<ChatSession>>>(
    '/chat/sessions',
    { params: { page: 0, size: 50 } },
  )

  const sessions = sessionsData?.data?.content ?? []

  // 세션 메시지 로드
  const loadMessages = useCallback(async (sessionId: number) => {
    try {
      const res = await apiClient.get<ApiResponse<{ messages: ChatMessageType[] }>>(
        `/chat/sessions/${sessionId}`,
      )
      setMessages(res.data.data.messages ?? [])
    } catch {
      setMessages([])
    }
  }, [])

  const handleSelectSession = useCallback(
    (session: ChatSession) => {
      if (isStreaming) abortStream()
      setActiveSessionId(session.id)
      setStreamingContent('')
      setStreamingSources([])
      loadMessages(session.id)
    },
    [isStreaming, abortStream, loadMessages],
  )

  // 새 세션 생성
  const handleCreateSession = async (title: string, documentIds: number[]) => {
    setCreatingSession(true)
    try {
      const res = await apiClient.post<ApiResponse<ChatSession>>('/chat/sessions', {
        title,
        documentIds,
      })
      const newSession = res.data.data
      await refetchSessions()
      setActiveSessionId(newSession.id)
      setMessages([])
      setStreamingContent('')
      setStreamingSources([])
      setNewSessionOpen(false)
    } catch {
      // 에러 처리는 생략 (UI에서 재시도 가능)
    } finally {
      setCreatingSession(false)
    }
  }

  // 메시지 전송 (SSE 스트리밍)
  const handleSend = useCallback(async () => {
    if (!activeSessionId || !inputValue.trim() || isStreaming) return

    const userContent = inputValue.trim()
    setInputValue('')

    // 사용자 메시지를 즉시 추가
    const userMessage: ChatMessageType = {
      id: Date.now(),
      role: 'USER',
      content: userContent,
      sources: [],
      createdAt: new Date().toISOString(),
    }
    setMessages((prev) => [...prev, userMessage])
    setStreamingContent('')
    setStreamingSources([])

    await startStream(
      `/api/v1/chat/sessions/${activeSessionId}/messages`,
      { content: userContent },
      {
        onEvent: {
          sources: (data) => {
            try {
              const parsed = JSON.parse(data) as { chunks: SourceChunk[] }
              setStreamingSources(parsed.chunks ?? [])
            } catch {
              // ignore
            }
          },
          answer: (data) => {
            try {
              const parsed = JSON.parse(data) as { content: string; done: boolean; messageId?: number }
              if (parsed.done) {
                // 스트리밍 완료 → 실제 메시지로 교체
                setMessages((prev) => [
                  ...prev,
                  {
                    id: parsed.messageId ?? Date.now(),
                    role: 'ASSISTANT',
                    content: streamingContentRef.current,
                    sources: streamingSourcesRef.current,
                    createdAt: new Date().toISOString(),
                  },
                ])
                setStreamingContent('')
                setStreamingSources([])
              } else {
                setStreamingContent((prev) => {
                  streamingContentRef.current = prev + parsed.content
                  return prev + parsed.content
                })
              }
            } catch {
              // ignore
            }
          },
        },
        onDone: () => {
          setStreamingContent('')
          setStreamingSources([])
        },
      },
    )
  }, [activeSessionId, inputValue, isStreaming, startStream])

  // streaming 참조 (클로저 이슈 방지)
  const streamingContentRef = useRef('')
  const streamingSourcesRef = useRef<SourceChunk[]>([])

  useEffect(() => {
    streamingContentRef.current = streamingContent
  }, [streamingContent])

  useEffect(() => {
    streamingSourcesRef.current = streamingSources
  }, [streamingSources])

  // 스크롤 맨 아래로
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streamingContent])

  // 스트리밍 중 가상 메시지 (타이핑 효과)
  const streamingMessage: ChatMessageType | null =
    isStreaming && (streamingContent || streamingSources.length > 0)
      ? {
          id: -1,
          role: 'ASSISTANT',
          content: streamingContent,
          sources: streamingSources,
          createdAt: new Date().toISOString(),
        }
      : null

  return (
    <div className="flex h-full -m-6 overflow-hidden">
      {/* ── 좌측: 세션 목록 ── */}
      <aside className="w-64 shrink-0 flex flex-col border-r border-gray-200 bg-white overflow-hidden">
        {/* 새 대화 버튼 */}
        <div className="p-4 border-b border-gray-100">
          <button
            type="button"
            onClick={() => setNewSessionOpen(true)}
            className="w-full flex items-center justify-center gap-2 rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700 transition-colors"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            새 대화
          </button>
        </div>

        {/* 세션 목록 */}
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {sessionsLoading ? (
            <div className="space-y-2 p-2">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-12 animate-pulse rounded-lg bg-gray-100" />
              ))}
            </div>
          ) : sessions.length === 0 ? (
            <p className="text-xs text-gray-400 text-center py-8 px-3">
              아직 대화가 없습니다.
              <br />새 대화를 시작해보세요.
            </p>
          ) : (
            sessions.map((session) => (
              <button
                key={session.id}
                type="button"
                onClick={() => handleSelectSession(session)}
                className={`w-full text-left rounded-lg px-3 py-2.5 transition-colors ${
                  activeSessionId === session.id
                    ? 'bg-sky-50 border border-sky-200'
                    : 'hover:bg-gray-50 border border-transparent'
                }`}
              >
                <p
                  className={`text-sm font-medium truncate ${
                    activeSessionId === session.id ? 'text-sky-700' : 'text-gray-800'
                  }`}
                >
                  {session.title}
                </p>
                <p className="text-xs text-gray-400 mt-0.5">{formatDate(session.updatedAt)}</p>
              </button>
            ))
          )}
        </div>
      </aside>

      {/* ── 우측: 채팅 인터페이스 ── */}
      <main className="flex flex-col flex-1 overflow-hidden bg-gray-50">
        {activeSessionId === null ? (
          // 빈 상태
          <div className="flex flex-1 items-center justify-center">
            <div className="text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-sky-100">
                <svg
                  className="h-8 w-8 text-sky-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
                  />
                </svg>
              </div>
              <h3 className="text-lg font-semibold text-gray-800 mb-1">DocMind AI 채팅</h3>
              <p className="text-sm text-gray-500 mb-4">
                문서를 기반으로 AI에게 질문하세요.
                <br />
                RAG 기술로 정확한 답변을 제공합니다.
              </p>
              <button
                type="button"
                onClick={() => setNewSessionOpen(true)}
                className="rounded-lg bg-sky-600 px-5 py-2 text-sm font-medium text-white hover:bg-sky-700 transition-colors"
              >
                새 대화 시작
              </button>
            </div>
          </div>
        ) : (
          <>
            {/* 메시지 영역 */}
            <div className="flex-1 overflow-y-auto px-6 py-4 space-y-2">
              {messages.length === 0 && !isStreaming && (
                <div className="text-center py-12">
                  <p className="text-sm text-gray-400">
                    아직 메시지가 없습니다. 질문을 입력해보세요.
                  </p>
                </div>
              )}

              {messages.map((msg) => (
                <ChatMessage key={msg.id} message={msg} />
              ))}

              {/* 스트리밍 중 타이핑 메시지 */}
              {streamingMessage && (
                <ChatMessage message={streamingMessage} isStreaming />
              )}

              {/* 연결 중 표시 */}
              {sseStatus === 'connecting' && !streamingContent && (
                <div className="flex justify-start mb-4">
                  <div className="max-w-xs rounded-2xl rounded-tl-sm bg-white border border-gray-200 px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-1.5">
                      {[0, 1, 2].map((i) => (
                        <span
                          key={i}
                          className="inline-block h-2 w-2 rounded-full bg-gray-400 animate-bounce"
                          style={{ animationDelay: `${i * 0.15}s` }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* 입력 영역 */}
            <div className="shrink-0 border-t border-gray-200 bg-white px-6 py-4">
              <ChatInput
                value={inputValue}
                onChange={setInputValue}
                onSubmit={handleSend}
                disabled={isStreaming}
                placeholder="문서에 대해 질문하세요... (Shift+Enter로 줄바꿈)"
              />
              <p className="mt-1.5 text-xs text-gray-400 text-center">
                AI가 문서 내용을 참조하여 답변합니다. 중요한 결정에는 반드시 원문을 확인하세요.
              </p>
            </div>
          </>
        )}
      </main>

      {/* 새 대화 다이얼로그 */}
      <NewSessionDialog
        open={newSessionOpen}
        onClose={() => setNewSessionOpen(false)}
        onConfirm={handleCreateSession}
        creating={creatingSession}
      />
    </div>
  )
}

export default ChatPage
