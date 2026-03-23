// ────────────────────────────────────────────
// 공통 API 응답 타입
// ────────────────────────────────────────────

export type ApiResponse<T> = {
  success: boolean
  data: T
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

export type ApiError = {
  success: false
  error: {
    code: string
    message: string
    details?: Record<string, string>
  }
  timestamp: string
}

// ────────────────────────────────────────────
// Auth
// ────────────────────────────────────────────

export type LoginRequest = {
  email: string
  password: string
}

export type SignupRequest = {
  email: string
  password: string
  name: string
}

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export type UserInfo = {
  id: number
  email: string
  name: string
  createdAt: string
}

// ────────────────────────────────────────────
// Document
// ────────────────────────────────────────────

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'READY'
  | 'FAILED'

export type DocumentSummary = {
  id: number
  content: string
  generatedAt: string
}

export type Document = {
  id: number
  title: string
  originalFileName: string
  fileSize: number
  mimeType: string
  status: DocumentStatus
  pageCount: number | null
  chunkCount: number | null
  createdAt: string
}

export type DocumentDetail = Document & {
  textLength: number | null
  tags: string[]
  summary: DocumentSummary | null
  errorMessage: string | null
}

export type DocumentUploadRequest = {
  title: string
  description?: string
}

// ────────────────────────────────────────────
// Chat Session & Message
// ────────────────────────────────────────────

export type ChatSession = {
  id: number
  title: string
  documentIds: number[]
  messageCount: number
  createdAt: string
  updatedAt: string
}

export type ChatMessageRole = 'USER' | 'ASSISTANT'

export type SourceChunk = {
  chunkId: number
  documentTitle: string
  content: string
  score: number
}

export type ChatMessage = {
  id: number
  role: ChatMessageRole
  content: string
  sources: SourceChunk[]
  createdAt: string
}

export type CreateSessionRequest = {
  documentIds: number[]
  title: string
}

export type SendMessageRequest = {
  content: string
}

// SSE 스트리밍 이벤트
export type SSESourcesEvent = {
  chunks: SourceChunk[]
}

export type SSEAnswerEvent = {
  content: string
  done: boolean
  messageId?: number
}

// ────────────────────────────────────────────
// Search
// ────────────────────────────────────────────

export type SearchType = 'full_text' | 'semantic' | 'hybrid'

export type SearchResult = {
  documentId: number
  documentTitle: string
  chunkId: number
  content: string
  score: number
  highlightedContent: string
  searchType: string
}

export type SearchResponse = {
  results: SearchResult[]
  totalCount: number
  page: number
  size: number
}

// ────────────────────────────────────────────
// Tag
// ────────────────────────────────────────────

export type Tag = {
  id: number
  name: string
  documentCount: number
}
