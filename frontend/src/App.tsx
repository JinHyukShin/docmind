import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from '@/components/layout/Layout'
import DocumentsPage from '@/pages/DocumentsPage'
import ChatPage from '@/pages/ChatPage'
import SearchPage from '@/pages/SearchPage'
import TagsPage from '@/pages/TagsPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<DocumentsPage />} />
        <Route path="chat" element={<ChatPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="tags" element={<TagsPage />} />
        {/* 404 fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
