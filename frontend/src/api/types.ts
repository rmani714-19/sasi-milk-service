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
  aadhaarMasked?: string;
  regionId?: number;
  joiningDate: string;
  status: string;
  paymentFrequency: 'WEEKLY' | 'MONTHLY';
  qrCode: string;
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
  qualityPending?: boolean;
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
  /** Set when the batch pays only one region/area. */
  regionId?: number | null;
  /** Payment-module status name (DRAFT, UNDER_REVIEW, READY_FOR_BANK, SUBMITTED_TO_BANK, COMPLETED …). */
  specStatus?: string;
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
  billNumber?: string | null;
  paymentReference?: string | null;
  specStatus?: string;
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
  billsGenerated: number;
  bankSubmission?: BankSubmission | null;
  items: SettlementItem[];
}

/** The record of handing a payment batch to the bank — nothing is sent from the app. */
export interface BankSubmission {
  submittedOn: string;
  bankReference?: string | null;
  note?: string | null;
  producerCount: number;
  totalAmount: number;
  submittedBy?: number | null;
  createdAt: string;
}

/** Settlement vs payments: what the batch expects, what is confirmed paid, and the same by region. */
export interface Reconciliation {
  batchTotal: number;
  linesTotal: number;
  paidTotal: number;
  pendingTotal: number;
  failedTotal: number;
  producers: number;
  paidCount: number;
  pendingCount: number;
  failedCount: number;
  linesMatchBatch: boolean;
  complete: boolean;
  difference: number;
  regions: {
    regionId?: number | null;
    producers: number;
    paidCount: number;
    pendingCount: number;
    failedCount: number;
    expected: number;
    paid: number;
    pending: number;
    failed: number;
    status: string;
  }[];
}

export interface StatusHistoryEntry {
  id: number;
  subjectType: 'SETTLEMENT' | 'ITEM';
  subjectId: number;
  fromStatus?: string | null;
  toStatus: string;
  note?: string | null;
  changedBy?: number | null;
  changedAt: string;
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
  /** Regions (areas) the driver collects from. */
  routeRegionIds?: number[];
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
  /** Litres collected on the driver's route in the month (snapshot at calculation). */
  routeLitres?: number;
  vehicleExpenses?: number;
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
  bankSubmission?: { submittedOn: string; bankReference?: string | null; note?: string | null; driverCount: number; totalAmount: number } | null;
}

export type VehicleType = 'CAR' | 'VAN' | 'MINI_TRUCK' | 'TRUCK' | 'OTHER';
export type VehicleLifecycleStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';

export interface Vehicle {
  id: number;
  code: string;
  vehicleNumber: string;
  vehicleType: VehicleType;
  make?: string;
  model?: string;
  modelYear?: number;
  driverId?: number;
  description?: string;
  capacityLitres?: number;
  registrationDate?: string;
  insuranceExpiry?: string;
  pollutionExpiry?: string;
  notes?: string;
  status: VehicleLifecycleStatus;
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

export interface VehicleDriverAssignment {
  id: number;
  vehicleId: number;
  driverId: number;
  assignedFrom: string;
  assignedTo?: string;
  assignedBy?: number;
  remarks?: string;
  createdAt: string;
}

export type VehicleDeviceType = 'GPS' | 'GPS_CAMERA' | 'DASHCAM' | 'MDVR' | 'OTHER';
export type VehicleDeviceStatus = 'ACTIVE' | 'INACTIVE' | 'REMOVED' | 'FAULTY';

export interface VehicleDevice {
  id: number;
  vehicleId?: number;
  deviceType: VehicleDeviceType;
  provider?: string;
  externalDeviceId: string;
  deviceModel?: string;
  imei?: string;
  serialNumber?: string;
  simNumberMasked?: string;
  firmwareVersion?: string;
  installationDate?: string;
  removedDate?: string;
  primary: boolean;
  lastSeenAt?: string;
  status: VehicleDeviceStatus;
}

export interface VehicleDeviceAssignment {
  id: number;
  vehicleId: number;
  deviceId: number;
  assignedFrom: string;
  assignedTo?: string;
  assignedBy?: number;
  removedBy?: number;
  remarks?: string;
  createdAt: string;
}

export interface VehicleLocationPoint {
  vehicleId: number;
  deviceId?: number;
  latitude: number;
  longitude: number;
  speedKmph?: number;
  directionDegrees?: number;
  altitude?: number;
  ignitionStatus?: 'ON' | 'OFF' | 'UNKNOWN';
  motionStatus?: 'MOVING' | 'IDLE' | 'STOPPED' | 'UNKNOWN';
  gpsAccuracy?: number;
  providerTimestamp: string;
  receivedAt: string;
}

export interface VehicleTrip {
  id: number;
  vehicleId: number;
  deviceId?: number;
  driverId?: number;
  startedAt: string;
  endedAt?: string;
  startLatitude?: number;
  startLongitude?: number;
  endLatitude?: number;
  endLongitude?: number;
  distanceKm?: number;
  durationSeconds?: number;
  maxSpeedKmph?: number;
  averageSpeedKmph?: number;
  status: 'ONGOING' | 'COMPLETED' | 'UNKNOWN';
}

export type VehicleCameraType = 'FRONT' | 'CABIN' | 'REAR' | 'SIDE' | 'OTHER';

export interface VehicleCamera {
  id: number;
  vehicleId: number;
  deviceId: number;
  cameraType: VehicleCameraType;
  cameraChannel?: number;
  cameraName?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'FAULTY';
}

export interface LiveStreamInfo {
  available: boolean;
  protocol?: string;
  url?: string;
  expiresAt?: string;
}

export interface PlaybackInfo {
  available: boolean;
  protocol?: string;
  url?: string;
  expiresAt?: string;
}

export interface VehicleRecording {
  recordingId: string;
  startedAt: string;
  endedAt?: string;
  protocol?: string;
}

export type VehicleAlertType =
  | 'OVERSPEED'
  | 'DEVICE_OFFLINE'
  | 'IGNITION_ON'
  | 'IGNITION_OFF'
  | 'HARSH_BRAKING'
  | 'HARSH_ACCELERATION'
  | 'GEOFENCE_ENTRY'
  | 'GEOFENCE_EXIT'
  | 'CAMERA_OFFLINE'
  | 'DEVICE_TAMPER'
  | 'ACCIDENT'
  | 'OTHER';

export interface VehicleAlert {
  id: number;
  vehicleId: number;
  deviceId?: number;
  driverId?: number;
  alertType: VehicleAlertType;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  message: string;
  latitude?: number;
  longitude?: number;
  speedKmph?: number;
  occurredAt: string;
  acknowledgedBy?: number;
  acknowledgedAt?: string;
  status: 'OPEN' | 'ACKNOWLEDGED';
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
