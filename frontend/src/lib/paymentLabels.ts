/**
 * Plain-language names for payment-batch and producer-payment statuses. The app never moves money —
 * the owner pays through the bank and records the outcome here — so the wording says "submitted to
 * bank" and "confirmed", not "initiated" or "processed".
 */
const BATCH: Record<string, string> = {
  DRAFT: 'Draft',
  CALCULATED: 'Calculated',
  REVIEWED: 'Under review',
  UNDER_VERIFICATION: 'Under review',
  VERIFIED: 'Verified',
  APPROVED: 'Approved – ready for bank',
  PAYOUT_INITIATED: 'Submitted to bank',
  PARTIALLY_PAID: 'Partially confirmed',
  PAID: 'Completed',
  PAYMENT_INITIATED: 'Submitted to bank',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

const LINE: Record<string, string> = {
  PENDING: 'Pending',
  INITIATED: 'Submitted to bank',
  PROCESSING: 'Submitted to bank',
  SUCCESS: 'Paid',
  FAILED: 'Failed',
  ON_HOLD: 'On hold',
  CANCELLED: 'Cancelled',
};

export const batchStatusLabel = (s: string) => BATCH[s] ?? s.replace(/_/g, ' ');
export const lineStatusLabel = (s: string) => LINE[s] ?? s.replace(/_/g, ' ');
