import type { DocumentStatus } from '@/types'
import type { BadgeVariant } from '@/components/ui/Badge'
import { Badge } from '@/components/ui/Badge'

const STATUS_CONFIG: Record<
  DocumentStatus,
  { variant: BadgeVariant; label: string }
> = {
  UPLOADED: { variant: 'gray', label: 'UPLOADED' },
  PARSING: { variant: 'yellow', label: 'PARSING' },
  CHUNKING: { variant: 'blue', label: 'CHUNKING' },
  EMBEDDING: { variant: 'purple', label: 'EMBEDDING' },
  READY: { variant: 'green', label: 'READY' },
  FAILED: { variant: 'red', label: 'FAILED' },
}

type StatusBadgeProps = {
  status: DocumentStatus
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? { variant: 'gray' as BadgeVariant, label: status }
  return <Badge variant={config.variant}>{config.label}</Badge>
}
