import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage as ChatMessageType } from '@/types'
import { SourceCard } from './SourceCard'

type ChatMessageProps = {
  message: ChatMessageType
  isStreaming?: boolean
}

export function ChatMessage({ message, isStreaming = false }: ChatMessageProps) {
  const isUser = message.role === 'USER'
  const [showSources, setShowSources] = useState(false)
  const hasSources = message.sources && message.sources.length > 0

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      <div className={`max-w-[75%] ${isUser ? 'items-end' : 'items-start'} flex flex-col gap-1`}>
        {/* 역할 레이블 */}
        <span className="text-xs text-gray-400 px-1">
          {isUser ? '나' : 'DocMind AI'}
        </span>

        {/* 말풍선 */}
        <div
          className={`rounded-2xl px-4 py-3 text-sm leading-relaxed ${
            isUser
              ? 'bg-sky-600 text-white rounded-tr-sm'
              : 'bg-white border border-gray-200 text-gray-800 rounded-tl-sm shadow-sm'
          }`}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap">{message.content}</p>
          ) : (
            <div className="prose prose-sm max-w-none prose-p:my-1 prose-headings:my-2 prose-ul:my-1 prose-ol:my-1">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {message.content}
              </ReactMarkdown>
              {isStreaming && (
                <span className="inline-block w-2 h-4 ml-0.5 bg-sky-500 animate-pulse rounded-sm" />
              )}
            </div>
          )}
        </div>

        {/* 참조 소스 */}
        {!isUser && hasSources && (
          <div className="w-full mt-1">
            <button
              type="button"
              onClick={() => setShowSources((v) => !v)}
              className="flex items-center gap-1 text-xs text-sky-600 hover:text-sky-700 transition-colors"
            >
              <svg
                className={`w-3.5 h-3.5 transition-transform ${showSources ? 'rotate-90' : ''}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5l7 7-7 7"
                />
              </svg>
              참조 소스 {message.sources.length}개 {showSources ? '숨기기' : '보기'}
            </button>

            {showSources && (
              <div className="mt-2 space-y-2">
                {message.sources.map((source, idx) => (
                  <SourceCard key={source.chunkId} source={source} index={idx} />
                ))}
              </div>
            )}
          </div>
        )}

        {/* 타임스탬프 */}
        <span className="text-xs text-gray-300 px-1">
          {formatTime(message.createdAt)}
        </span>
      </div>
    </div>
  )
}

function formatTime(isoStr: string): string {
  try {
    const d = new Date(isoStr)
    return d.toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return ''
  }
}
