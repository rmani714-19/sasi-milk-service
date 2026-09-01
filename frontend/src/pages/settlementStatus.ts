type ChipColor = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info';

export function statusColor(status: string): ChipColor {
  switch (status) {
    case 'DRAFT':
      return 'default';
    case 'CALCULATED':
      return 'info';
    case 'REVIEWED':
      return 'warning';
    case 'APPROVED':
    case 'PAID':
      return 'success';
    case 'PAYOUT_INITIATED':
    case 'PARTIALLY_PAID':
      return 'primary';
    case 'FAILED':
    case 'CANCELLED':
      return 'error';
    default:
      return 'default';
  }
}
