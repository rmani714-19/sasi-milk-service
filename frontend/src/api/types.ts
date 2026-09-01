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
  email?: string;
  address?: string;
  regionId?: number;
  joiningDate: string;
  status: string;
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
  regionId?: number;
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

export interface SettlementDetail extends SettlementSummary {
  roundingMode: string;
  roundingScale: number;
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
