import Chip from '@mui/material/Chip';
import type { SxProps, Theme } from '@mui/material/styles';
import { STATUS_TOKENS } from '../theme';

export type StatusTone = keyof typeof STATUS_TOKENS;

/** Map a backend status string to a semantic colour tone. */
export function statusTone(status: string | null | undefined): StatusTone {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'APPROVED':
    case 'PAID':
    case 'SENT':
    case 'DELIVERED':
    case 'COMPLETED':
    case 'ENABLED':
    case 'SUCCESS':
      return 'positive';
    case 'PAYOUT_INITIATED':
    case 'PARTIALLY_PAID':
    case 'PROCESSING':
    case 'INITIATED':
    case 'REVIEWED':
      return 'brand';
    case 'PENDING':
    case 'QUEUED':
    case 'ON_HOLD':
    case 'DUE':
    case 'CALCULATED':
      return 'pending';
    case 'FAILED':
    case 'CANCELLED':
    case 'REJECTED':
    case 'REVERSED':
    case 'ERROR':
      return 'danger';
    case 'DRAFT':
    case 'INACTIVE':
    case 'ARCHIVED':
    case 'DISABLED':
    case 'CLOSED':
      return 'neutral';
    default:
      return 'info';
  }
}

const PRETTY = (s: string) =>
  s.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

export function StatusChip({
  status,
  label,
  tone,
  sx,
}: {
  status: string;
  label?: string;
  tone?: StatusTone;
  sx?: SxProps<Theme>;
}) {
  const t = STATUS_TOKENS[tone ?? statusTone(status)];
  return (
    <Chip
      size="small"
      label={label ?? PRETTY(status)}
      sx={{
        bgcolor: t.bg,
        color: t.fg,
        fontWeight: 600,
        border: 'none',
        '& .MuiChip-label': { px: 1 },
        ...sx,
      }}
    />
  );
}
