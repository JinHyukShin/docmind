import { useState, useEffect, useRef, useCallback } from 'react'
import { JWT_STORAGE_KEY } from '@/api/client'

// ─── 타입 ─────────────────────────────────────────────────────────────────────

export type SSEStatus = 'idle' | 'connecting' | 'streaming' | 'done' | 'error'

export type SSEOptions = {
  /** JWT 토큰을 쿼리 파라미터로 전달할지 여부 */
  withToken?: boolean
  /** 이벤트 이름 → 핸들러 매핑 */
  onEvent?: Record<string, (data: string) => void>
  /** 스트리밍 완료 콜백 */
  onDone?: () => void
  /** 에러 콜백 */
  onError?: (msg: string) => void
}

/**
 * Chat SSE 스트리밍 훅 (POST 요청 후 SSE 수신)
 *
 * 백엔드가 POST /api/v1/chat/sessions/{id}/messages 에 대해
 * SSE 응답을 반환하는 패턴에 맞게 구현.
 * EventSource 는 GET만 지원하므로, fetch + ReadableStream으로 구현.
 */
export function useStreamingChat() {
  const [status, setStatus] = useState<SSEStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const start = useCallback(
    async (
      url: string,
      body: unknown,
      options: SSEOptions = {},
    ) => {
      // 이전 스트림 중단
      if (abortRef.current) {
        abortRef.current.abort()
      }

      const controller = new AbortController()
      abortRef.current = controller

      const token = localStorage.getItem(JWT_STORAGE_KEY)
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      }
      if (options.withToken !== false && token) {
        headers['Authorization'] = `Bearer ${token}`
      }

      setStatus('connecting')
      setError(null)

      try {
        const response = await fetch(url, {
          method: 'POST',
          headers,
          body: JSON.stringify(body),
          signal: controller.signal,
        })

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        if (!response.body) {
          throw new Error('Response body is null')
        }

        setStatus('streaming')

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''

          let currentEvent = 'message'
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              if (data === '[DONE]') {
                setStatus('done')
                options.onDone?.()
                return
              }
              options.onEvent?.[currentEvent]?.(data)
              // 이벤트명 리셋
              currentEvent = 'message'
            }
          }
        }

        setStatus('done')
        options.onDone?.()
      } catch (err: unknown) {
        if ((err as { name?: string }).name === 'AbortError') {
          setStatus('idle')
          return
        }
        const msg =
          typeof err === 'object' && err !== null && 'message' in err
            ? String((err as { message: unknown }).message)
            : '스트리밍 오류가 발생했습니다.'
        setError(msg)
        setStatus('error')
        options.onError?.(msg)
      }
    },
    [],
  )

  const abort = useCallback(() => {
    abortRef.current?.abort()
    setStatus('idle')
  }, [])

  // 언마운트 시 정리
  useEffect(() => {
    return () => {
      abortRef.current?.abort()
    }
  }, [])

  return { status, error, start, abort }
}

// ─── 일반 SSE (GET 기반 단방향 스트리밍) ────────────────────────────────────

export type UseSSEReturn<T> = {
  data: T | null
  status: 'connecting' | 'connected' | 'error' | 'closed'
  error: string | null
  disconnect: () => void
  reconnect: () => void
}

/**
 * 범용 SSE GET 구독 훅 (AI 요약 생성 등 GET SSE 엔드포인트용)
 */
export function useSSE<T>(
  url: string | null,
  eventName: string = 'message',
  withToken: boolean = true,
): UseSSEReturn<T> {
  const [data, setData] = useState<T | null>(null)
  const [status, setStatus] = useState<'connecting' | 'connected' | 'error' | 'closed'>('closed')
  const [error, setError] = useState<string | null>(null)

  const esRef = useRef<EventSource | null>(null)
  const retryCountRef = useRef(0)
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)
  const MAX_RETRIES = 5
  const RETRY_DELAY = 3000

  const clearRetryTimer = () => {
    if (retryTimerRef.current) {
      clearTimeout(retryTimerRef.current)
      retryTimerRef.current = null
    }
  }

  const close = useCallback(() => {
    clearRetryTimer()
    if (esRef.current) {
      esRef.current.close()
      esRef.current = null
    }
    if (mountedRef.current) setStatus('closed')
  }, [])

  const connect = useCallback(() => {
    if (!url) return

    if (esRef.current) {
      esRef.current.close()
      esRef.current = null
    }

    let fullUrl = url
    if (withToken) {
      const token = localStorage.getItem(JWT_STORAGE_KEY)
      if (token) {
        const separator = url.includes('?') ? '&' : '?'
        fullUrl = `${url}${separator}token=${encodeURIComponent(token)}`
      }
    }

    if (mountedRef.current) {
      setStatus('connecting')
      setError(null)
    }

    const es = new EventSource(fullUrl)
    esRef.current = es

    es.addEventListener(eventName, (e: MessageEvent) => {
      if (!mountedRef.current) return
      try {
        const parsed = JSON.parse(e.data) as T
        setData(parsed)
        setStatus('connected')
        retryCountRef.current = 0
      } catch {
        setData(e.data as unknown as T)
      }
    })

    es.addEventListener('open', () => {
      if (mountedRef.current) {
        setStatus('connected')
        setError(null)
        retryCountRef.current = 0
      }
    })

    es.addEventListener('error', () => {
      if (!mountedRef.current) return
      es.close()
      esRef.current = null
      setStatus('error')

      if (retryCountRef.current < MAX_RETRIES) {
        retryCountRef.current += 1
        setError(`연결 오류. ${RETRY_DELAY / 1000}초 후 재시도... (${retryCountRef.current}/${MAX_RETRIES})`)
        retryTimerRef.current = setTimeout(() => {
          if (mountedRef.current) connect()
        }, RETRY_DELAY)
      } else {
        setError('SSE 연결에 실패했습니다. 페이지를 새로고침 해주세요.')
      }
    })
  }, [url, eventName, withToken]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    mountedRef.current = true
    if (url) {
      retryCountRef.current = 0
      connect()
    }
    return () => {
      mountedRef.current = false
      clearRetryTimer()
      if (esRef.current) {
        esRef.current.close()
        esRef.current = null
      }
    }
  }, [url]) // connect 를 deps 에서 제외해 무한 루프 방지

  return {
    data,
    status,
    error,
    disconnect: close,
    reconnect: () => {
      retryCountRef.current = 0
      connect()
    },
  }
}
