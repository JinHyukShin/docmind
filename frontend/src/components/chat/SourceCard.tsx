import type { SourceChunk } from '@/types'

type SourceCardProps = {
  source: SourceChunk
  index: number
}

export function SourceCard({ source, index }: SourceCardProps) {
  const scorePercent = Math.round(source.score * 100)

  return (
    <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs">
      <div className="flex items-center justify-between mb-1.5">
        <span className="font-semibold text-gray-700 truncate max-w-[70%]">
          [{index + 1}] {source.documentTitle}
        </span>
        <span
          className={`font-bold tabular-nums ${
            scorePercent >= 85
              ? 'text-green-600'
              : scorePercent >= 70
              ? 'text-yellow-600'
              : 'text-gray-500'
          }`}
        >
          {scorePercent}%
        </span>
      </div>
      <p className="text-gray-600 leading-relaxed line-clamp-3">{source.content}</p>
    </div>
  )
}
