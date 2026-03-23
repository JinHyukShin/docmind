import { useLocation } from 'react-router-dom'

const pageTitles: Record<string, string> = {
  '/': '문서 관리',
  '/chat': 'AI 채팅',
  '/search': '검색',
  '/tags': '태그',
}

function Header() {
  const location = useLocation()

  const isChatDetail = location.pathname.startsWith('/chat/')
  const pageTitle = isChatDetail
    ? 'AI 채팅'
    : (pageTitles[location.pathname] ?? 'DocMind')

  return (
    <header
      className="flex items-center justify-between h-16 px-6 bg-white border-b border-gray-200 shrink-0"
      role="banner"
    >
      {/* 현재 페이지 제목 */}
      <h1 className="text-lg font-semibold text-gray-800">{pageTitle}</h1>

      {/* 우측 영역 */}
      <div className="flex items-center gap-4">
        {/* AI 상태 표시 */}
        <div className="flex items-center gap-1.5" aria-label="AI 서비스 상태">
          <span
            className="inline-block w-2 h-2 rounded-full bg-green-500 animate-pulse"
            aria-hidden="true"
          />
          <span className="text-sm text-gray-500">AI Ready</span>
        </div>

        {/* 구분선 */}
        <div className="w-px h-6 bg-gray-200" aria-hidden="true" />

        {/* 사용자 아바타 */}
        <button
          type="button"
          className="flex items-center gap-2 text-sm text-gray-700 hover:text-gray-900 transition-colors"
          aria-label="사용자 메뉴 열기"
        >
          <span
            className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-sky-600 text-white text-xs font-bold select-none"
            aria-hidden="true"
          >
            U
          </span>
          <span className="hidden sm:inline">User</span>
        </button>
      </div>
    </header>
  )
}

export default Header
