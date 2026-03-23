import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'

function Layout() {
  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* 왼쪽 사이드바 (240px 고정) */}
      <Sidebar />

      {/* 오른쪽 메인 영역 */}
      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        {/* 상단 헤더 (64px 고정) */}
        <Header />

        {/* 페이지 컨텐츠 (스크롤 가능) */}
        <main
          id="main-content"
          className="flex-1 overflow-y-auto p-6"
          tabIndex={-1}
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default Layout
