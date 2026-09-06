export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Region {
  id: number;
  code: string;
  name: string;
  description?: string;
  status: string;
}

export interface Producer {
  id: number;
  publicId: string;
  producerNumber: string;
  producerName: string;
  mobileNumber?: string;
  alternateMobile?: string;
  email?: string;
  address?: string;
  upiId?: string;
  aadhaarMasked?: string;
  regionId?: number;
  joiningDate: string;
  status: string;
  paymentFrequency: 'WEEKLY' | 'MONTHLY';
  qrCode: string;
}

export interface BankAccount {
  id: number;
  accountHolderName: string;
  accountNumberMasked: string;
  ifscCode: string;
  bankName?: string;
  bankBranch?: string;
  primary: boolean;
  status: string;
}

export interface Breakdown {
  tsValue: number;
  rateScope: string;
  ratePerLitre: number;
  incentiveRatePerLitre: number;
  baseAmount: number;
  incentiveAmount: number;
  deductionAmount: number;
  netAmount: number;
}

export interface Collection {
  id: string;
  collectionNumber: string;
  producerId: number;
  producerNumber?: string;
  producerName?: string;
  collectionDate: string;
  shift: string;
  litres: number;
  fat: number;
  snf: number;
  status: string;
  breakdown: Breakdown;
  duplicate: boolean;
}

export interface RateChart {
  id: number;
  scope: 'COMMON' | 'REGION';
  regionId?: number;
  name?: string;
  tsValue: number;
  effectiveFrom: string;
  effectiveTo?: string;
  status: string;
  note?: string;
}

export interface RatePreview {
  ratePerLitre: number;
  tsValue: number;
  scope: string;
  missing: boolean;
}

export interface SettlementSummary {
  id: string;
  internalId: number;
  settlementNumber: string;
  paymentFrequency: 'WEEKLY' | 'MONTHLY' | 'ALL';
  periodStart: string;
  periodEnd: string;
  status: string;
  totalProducers: number;
  totalLitres: number;
  totalNetPayable: number;
}

export interface SettlementItem {
  id: number;
  producerId: number;
  producerNumber?: string;
  producerName?: string;
  regionId?: number;
  verificationStatus: 'PENDING_VERIFICATION' | 'VERIFIED';
  verifiedAmount?: number;
  verifiedAt?: string;
  morningLitres: number;
  eveningLitres: number;
  totalLitres: number;
  collectionCount: number;
  averageRate: number;
  milkAmount: number;
  incentiveAmount: number;
  grossAmount: number;
  feedDeduction: number;
  veterinaryDeduction: number;
  otherDeduction: number;
  advanceRecovery: number;
  totalDeduction: number;
  adjustmentAmount: number;
  netBeforeRounding: number;
  roundOff: number;
  netPayable: number;
  paymentStatus: string;
}

export interface DailyCollectionRow {
  collectionId: number;
  collectionNumber: string;
  collectionDate: string;
  shift: string;
  litres: number;
  fatPercentage: number;
  snfPercentage: number;
  ratePerLitre: number;
  milkAmount: number;
}

export interface SettlementDetail extends SettlementSummary {
  roundingMode: string;
  roundingScale: number;
  verifiedCount: number;
  pendingVerificationCount: number;
  totalMilkAmount: number;
  totalIncentive: number;
  totalDeduction: number;
  totalAdvanceRecovery: number;
  totalAdjustment: number;
  totalRoundOff: number;
  items: SettlementItem[];
}

export interface Payout {
  id: number;
  publicId: string;
  settlementId: number;
  settlementItemId: number;
  producerId: number;
  amount: number;
  provider: string;
  providerPayoutId?: string;
  status: string;
  utr?: string;
  failureReason?: string;
  attemptCount: number;
  settledAt?: string;
}

export interface Driver {
  id: number;
  code: string;
  name: string;
  mobile?: string;
  joiningDate?: string;
  vehicleId?: number;
  salaryType: 'FIXED' | 'PER_DAY' | 'CUSTOM';
  monthlySalary: number;
  perDayRate?: number;
  status: string;
  note?: string;
  accountHolderName?: string;
  accountNumberMasked?: string;
  ifscCode?: string;
  bankName?: string;
  branchName?: string;
  bankComplete: boolean;
  bankUpdatedAt?: string;
}

export interface DriverBankDetails {
  accountHolderName: string;
  accountNumber: string;
  ifscCode: string;
  bankName: string;
  branchName?: string;
  bankUpdatedAt?: string;
  bankUpdatedBy?: number;
}

export interface SalaryItem {
  id: number;
  batchId: number;
  driverId: number;
  vehicleId?: number;
  salaryType: string;
  basicSalary: number;
  workingDays: number;
  allowances: number;
  overtime: number;
  otherEarnings: number;
  salaryAdvance: number;
  loanRecovery: number;
  penalty: number;
  otherDeduction: number;
  grossAmount: number;
  totalDeduction: number;
  netSalary: number;
  paymentStatus: string;
  paymentMethod?: string;
  paymentReference?: string;
  paidAt?: string;
  failureReason?: string;
  note?: string;
}

export interface SalaryBatchSummary {
  id: number;
  batchNumber: string;
  batchType: string;
  periodMonth: string;
  status: string;
  totalDrivers: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  totalPaid: number;
  approvedAt?: string;
}

export interface SalaryBatchDetail extends SalaryBatchSummary {
  totalAllowances: number;
  totalOvertime: number;
  calculatedAt?: string;
  reviewedAt?: string;
  cancelledAt?: string;
  items: SalaryItem[];
}

export interface Vehicle {
  id: number;
  code: string;
  registrationNumber: string;
  description?: string;
  capacityLitres?: number;
  status: string;
}

export interface VehicleStatus {
  vehicleId: number;
  code: string;
  registrationNumber: string;
  online: boolean;
  latitude?: number;
  longitude?: number;
  speedKmph?: number;
  lastFixAt?: string;
}

export interface NotificationChannel {
  channel: string;
  enabled: boolean;
  provider?: string;
  senderId?: string;
}

export interface NotificationTemplateItem {
  id: number;
  code: string;
  channel: string;
  locale: string;
  subject?: string;
  body: string;
  providerTemplateId?: string;
  status: string;
}

export interface NotificationLogItem {
  id: number;
  eventType: string;
  channel: string;
  recipient: string;
  producerId?: number;
  status: string;
  attemptCount: number;
  renderedBody?: string;
  sentAt?: string;
}

export interface CenterSettings {
  centerName: string;
  ownerName?: string;
  mobileNumber?: string;
  email?: string;
  address?: string;
  currencyCode: string;
  settlementDayOfWeek: number;
  roundingMode: string;
  roundingScale: number;
  incentiveStacking: string;
  incentiveMaxPerLitre: number;
  allowMissingRate: boolean;
  fatMin: number;
  fatMax: number;
  snfMin: number;
  snfMax: number;
}

export interface CenterBankAccount {
  id: number;
  accountHolderName: string;
  accountNumberMasked: string;
  ifsc: string;
  bankName: string;
  label?: string;
  status: 'ACTIVE' | 'INACTIVE';
  verified: boolean;
  primary: boolean;
  verifiedAt?: string;
}

export type IncentiveTarget = 'PRODUCER' | 'MEDIATOR';
export type IncentiveType =
  | 'PER_LITRE'
  | 'FIXED_AMOUNT'
  | 'VOLUME_BONUS'
  | 'DAILY_BONUS'
  | 'WEEKLY_BONUS'
  | 'MONTHLY_BONUS'
  | 'RETENTION_BONUS'
  | 'NEW_PRODUCER_BONUS'
  | 'COMPETITOR_RETENTION';

export interface Incentive {
  id: number;
  publicId: string;
  name: string;
  target: IncentiveTarget;
  incentiveType: IncentiveType;
  amount: number;
  effectiveFrom: string;
  effectiveTo: string;
  status: 'ACTIVE' | 'INACTIVE';
  effectiveStatus: 'ACTIVE' | 'INACTIVE' | 'EXPIRED';
  description?: string;
  priority: number;
  used: boolean;
  createdBy?: number;
  createdByName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BankBalanceCheck {
  id: number;
  centerBankAccountId?: number;
  bankAccountLabel: string;
  availableBalance: number;
  requiredAmount: number;
  remainingAfter: number;
  sufficient: boolean;
  method: 'MANUAL' | 'AUTOMATIC';
  note?: string;
  verifiedBy?: number;
  verifiedAt: string;
}

export interface PreValidationCheck {
  key: string;
  label: string;
  passed: boolean;
  blocking: boolean;
  detail?: string;
  link?: string;
}

export interface PreValidationReport {
  result: 'READY_FOR_APPROVAL' | 'BLOCKED';
  checks: PreValidationCheck[];
}

export type ExpenseCategory =
  | 'FUEL'
  | 'MAINTENANCE'
  | 'RENT'
  | 'UTILITIES'
  | 'OFFICE_SUPPLIES'
  | 'TRANSPORT'
  | 'SALARY_OTHER'
  | 'MISC'
  | 'OTHER';

export interface Expense {
  id: number;
  expenseNumber: string;
  category: ExpenseCategory;
  description?: string;
  amount: number;
  expenseDate: string;
  paymentMethod: 'CASH' | 'BANK_TRANSFER' | 'UPI' | 'CHEQUE' | 'OTHER';
  paymentReference?: string;
  status: 'PAID' | 'FAILED' | 'CANCELLED';
  paidAt: string;
}

export type PaymentFlagSubjectType = 'PAYOUT' | 'SALARY_ITEM' | 'EXPENSE';

export interface PaymentFlag {
  id: number;
  subjectType: PaymentFlagSubjectType;
  subjectId: number;
  note: string;
  status: 'OPEN' | 'RESOLVED';
  flaggedBy?: number;
  flaggedAt: string;
  resolutionNote?: string;
  resolvedBy?: number;
  resolvedAt?: string;
}
