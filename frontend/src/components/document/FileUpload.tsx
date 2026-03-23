import React, { useRef, useState, useCallback } from 'react'

type FileUploadProps = {
  onFileSelect: (file: File) => void
  accept?: string
  maxSizeMB?: number
}

const ACCEPTED_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
]

const ACCEPTED_EXTENSIONS = '.pdf,.docx,.txt'

export function FileUpload({
  onFileSelect,
  accept = ACCEPTED_EXTENSIONS,
  maxSizeMB = 50,
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const validateAndSelect = useCallback(
    (file: File) => {
      setErrorMsg(null)

      const isValidType =
        ACCEPTED_MIME_TYPES.includes(file.type) ||
        file.name.endsWith('.pdf') ||
        file.name.endsWith('.docx') ||
        file.name.endsWith('.txt')

      if (!isValidType) {
        setErrorMsg('PDF, DOCX, TXT 파일만 업로드 가능합니다.')
        return
      }

      const maxBytes = maxSizeMB * 1024 * 1024
      if (file.size > maxBytes) {
        setErrorMsg(`파일 크기는 ${maxSizeMB}MB 이하여야 합니다.`)
        return
      }

      onFileSelect(file)
    },
    [onFileSelect, maxSizeMB],
  )

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }

  const handleDragLeave = () => setIsDragging(false)

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) validateAndSelect(file)
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) validateAndSelect(file)
  }

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        aria-label="파일 업로드 영역. 클릭하거나 파일을 드래그하세요"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click()
        }}
        className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 cursor-pointer transition-colors ${
          isDragging
            ? 'border-sky-500 bg-sky-50'
            : 'border-gray-300 bg-gray-50 hover:border-sky-400 hover:bg-sky-50'
        }`}
      >
        <svg
          className={`h-10 w-10 mb-3 ${isDragging ? 'text-sky-500' : 'text-gray-400'}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
          />
        </svg>
        <p className="text-sm font-medium text-gray-700">
          파일을 드래그하거나 <span className="text-sky-600 underline">클릭</span>하여 업로드
        </p>
        <p className="mt-1 text-xs text-gray-400">PDF, DOCX, TXT (최대 {maxSizeMB}MB)</p>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={handleChange}
        aria-hidden="true"
      />

      {errorMsg && (
        <p className="mt-2 text-sm text-red-600" role="alert">
          {errorMsg}
        </p>
      )}
    </div>
  )
}
