import { useState, useEffect, useCallback, useRef } from 'react'
import type { AxiosRequestConfig } from 'axios'
import apiClient from '@/api/client'

// ─── 타입 ─────────────────────────────────────────────────────────────────────

export type UseFetchState<T> = {
  data: T | null
  loading: boolean
  error: string | null
}

export type UseFetchReturn<T> = UseFetchState<T> & {
  refetch: () => void
}

// ─── useFetch: URL 기반 자동 호출 훅 ─────────────────────────────────────────

export function useFetch<T>(
  url: string | null,
  config?: AxiosRequestConfig,
  deps: unknown[] = [],
): UseFetchReturn<T> {
  const [state, setState] = useState<UseFetchState<T>>({
    data: null,
    loading: false,
    error: null,
  })

  const configRef = useRef(config)
  configRef.current = config

  const fetchData = useCallback(async () => {
    if (!url) return

    setState((prev) => ({ ...prev, loading: true, error: null }))

    try {
      const response = await apiClient.request<T>({ url, ...configRef.current })
      setState({ data: response.data, loading: false, error: null })
    } catch (err: unknown) {
      const message = extractErrorMessage(err)
      setState({ data: null, loading: false, error: message })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, ...deps])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { ...state, refetch: fetchData }
}

// ─── useApiCall: 수동 호출(mutation) 훅 ──────────────────────────────────────

export type UseApiCallState<T> = {
  data: T | null
  loading: boolean
  error: string | null
}

export type UseApiCallReturn<T, P = unknown> = UseApiCallState<T> & {
  execute: (payload?: P, extraConfig?: AxiosRequestConfig) => Promise<T | null>
  reset: () => void
}

export function useApiCall<T, P = unknown>(
  url: string,
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' = 'POST',
): UseApiCallReturn<T, P> {
  const [state, setState] = useState<UseApiCallState<T>>({
    data: null,
    loading: false,
    error: null,
  })

  const execute = useCallback(
    async (payload?: P, extraConfig?: AxiosRequestConfig): Promise<T | null> => {
      setState({ data: null, loading: true, error: null })
      try {
        const response = await apiClient.request<T>({
          url,
          method,
          data: payload,
          ...extraConfig,
        })
        setState({ data: response.data, loading: false, error: null })
        return response.data
      } catch (err: unknown) {
        const message = extractErrorMessage(err)
        setState({ data: null, loading: false, error: message })
        return null
      }
    },
    [url, method],
  )

  const reset = useCallback(() => {
    setState({ data: null, loading: false, error: null })
  }, [])

  return { ...state, execute, reset }
}

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

function extractErrorMessage(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const e = err as Record<string, unknown>
    if (e.response && typeof e.response === 'object') {
      const resp = e.response as Record<string, unknown>
      if (resp.data && typeof resp.data === 'object') {
        const data = resp.data as Record<string, unknown>
        if (data.error && typeof data.error === 'object') {
          const apiErr = data.error as Record<string, unknown>
          if (typeof apiErr.message === 'string') return apiErr.message
        }
        if (typeof data.message === 'string') return data.message
      }
    }
    if (typeof e.message === 'string') return e.message
  }
  return '알 수 없는 오류가 발생했습니다.'
}
