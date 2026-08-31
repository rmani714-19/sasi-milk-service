# SASI MILK SERVICE — Architecture & Data Model (Design Phase)

Status: **DESIGN ONLY** — no application code yet. This document + the Flyway
migrations under `backend/src/main/resources/db/migration` + the calculation-service
interfaces under `backend/src/main/java` are the deliverables of this phase.

---

## 1. Architectural style

Modular monolith. **One** deployable Spring Boot application, strong internal module
boundaries, REST API, PostgreSQL. No microservices, no Kafka, no Redis, no
Elasticsearch in the initial build (per spec `do_not_use_initially`).

```
React 19 + TS + Vite (PWA, offline collection)
        │  HTTPS  /api/v1
        ▼
Spring Boot (Java 21) — modular monolith
  ├─ REST controllers (DTO in / DTO out, never JPA entities)
  ├─ Application services (transaction boundary, authorization)
  ├─ Domain: pure calculation services (BigDecimal, no I/O)  ◄── unit-tested in isolation
  ├─ Repositories (Spring Data JPA + Hibernate)
  ├─ Outbox → SQS → async workers (notifications, report generation)
  └─ Provider adapters (payout, SMS, WhatsApp, email, GPS)  behind interfaces
        │
        ▼
PostgreSQL (AWS RDS)   +   S3 (reports, logo, documents)
```

## 2. Module map

Package root: `com.sasimilkservice`

| Module | Package | Owns (tables) | Publishes (SPI) |
|---|---|---|---|
| auth | `.auth` | `refresh_tokens` | `AuthenticationFacade`, `CurrentUser` |
| user | `.user` | `users`, `user_roles` | `UserDirectory` |
| role | `.role` | `roles` | `PermissionResolver` |
| center | `.center` | `center` | `CenterSettings` (rounding, currency, FAT/SNF bounds, incentive stacking) |
| region | `.region` | `regions` | `RegionDirectory` |
| producer | `.producer` | `producers`, `producer_bank_accounts`, `producer_region_history` | `ProducerDirectory`, `BankAccountResolver` |
| rate | `.rate` | `rate_charts` (`ts_value` coefficient, effective-dated) | `RateResolutionService`, `RateFormula` |
| incentive | `.incentive` | `incentive_rules`, `producer_incentives` | `IncentiveCalculator` |
| deduction | `.deduction` | `deduction_types`, `deduction_transactions` | `DeductionCalculator`, `PendingDeductionQuery` |
| advance | `.advance` | `advance_accounts`, `advance_transactions` | `AdvanceRecoveryCalculator`, `AdvanceLedger` |
| collection | `.collection` | `milk_collections`, `milk_quality`, `collection_sync_log` | `CollectionAmountCalculator`, `CollectionQuery` |
| settlement | `.settlement` | `settlements`, `settlement_items`, `settlement_adjustments` | `SettlementCalculator`, `SettlementQuery` |
| payout | `.payout` | `payouts`, `payout_transactions` | `PayoutFacade` |
| notification | `.notification` | `notification_templates`, `notification_channel_config`, `notifications` | `NotificationPublisher` (fire-and-forget) |
| report | `.report` | — (reads via other modules' query SPIs + read models) | `ReportService` |
| vehicle | `.vehicle` | `vehicles`, `vehicle_devices`, `vehicle_locations`, `camera_devices` | `VehicleStatusQuery` (Phase 2) |
| audit | `.audit` | `audit_logs` | `AuditRecorder` |
| common | `.common` | — | money math, error model, pagination, ids, `ClientTxnId` |
| config | `.config` | — | `SecurityConfig`, `OpenApiConfig`, `JpaConfig`, `AsyncConfig`, `FlywayConfig` |

### Boundary rules (enforced by review; optionally Spring Modulith)

1. A module may reference another module **only** through its `spi` package
   (interfaces + immutable DTOs/records). Never another module's `domain`,
   `repository`, or `internal` package.
2. No cross-module JPA associations and no SQL joins across module-owned tables.
   Settlement composes data by calling `RateResolutionService`,
   `IncentiveCalculator`, `DeductionCalculator`, `AdvanceRecoveryCalculator`,
   `CollectionQuery`, `BankAccountResolver` — not by joining their tables.
3. Calculation services (`*Calculator`, `RateResolutionService`,
   `RoundingRule`) are **pure**: deterministic, no repository access, no clock,
   no randomness. All inputs are passed in; all money is `BigDecimal`.
4. Persistence-touching orchestration lives in `@Transactional` application
   services that call the pure calculators.
5. Controllers map DTO↔command, delegate to one application service, and never
   contain business rules.

## 3. Cross-cutting decisions

- **Money & quantity**: `BigDecimal` in Java, `NUMERIC` in PostgreSQL. Never
  `double`/`float`. Scales fixed per column (see `docs/DATA_MODEL.md`). Rounding
  mode/scale are **configuration** (`center.rounding_mode`, `center.rounding_scale`),
  not hard-coded, and are snapshotted onto every settlement. Client rule "round as
  per banking" → default `HALF_EVEN` at scale `0` (nearest whole rupee).
- **Idempotency**:
  - Collections: client-generated `client_transaction_id` (UUID), `UNIQUE`.
  - Payouts: `idempotency_key`, `UNIQUE`; provider called at-most-once per key.
  - Notifications: `dedupe_key`, `UNIQUE`.
- **Snapshots / reproducibility**: the applied rate, incentive rate, and full
  calculation breakdown are stored on `milk_collections`; the full per-producer
  and header calculation breakdown + engine version are stored on
  `settlements` / `settlement_items`. Historical records are **never**
  recomputed when a rate/incentive/rule changes.
- **Optimistic locking**: `version bigint` column on every mutable financial
  aggregate root (`users`, `producers`, `producer_bank_accounts`, `rate_charts`,
  `incentive_rules`, `producer_incentives`, `milk_collections`,
  `deduction_transactions`, `advance_accounts`, `settlements`, `settlement_items`,
  `payouts`) → mapped to `@Version`.
- **Soft delete**: financial/historical rows are never physically deleted.
  `status` transitions (`VOID`, `CANCELLED`, `INACTIVE`, `REVERSED`) + reversal
  transactions instead.
- **Audit**: `audit.AuditRecorder` writes `audit_logs` (append-only, JSONB
  old/new) for every event in the spec `audit.events` list. Financial mutations
  publish an audit event in the same transaction.
- **Async**: transactional outbox → SQS. Notification send, report rendering,
  and payout webhooks-driven follow-ups run on async workers. API requests
  never block on SMS/WhatsApp/email/report generation.
- **Auth**: JWT access token (short TTL) + rotating refresh token (hash stored,
  `replaced_by` chain). RBAC by permission code; backend authorization enforced
  independently of UI hiding (`@PreAuthorize` on application-service methods).
- **Time**: all timestamps `timestamptz`, stored UTC. Business dates
  (`collection_date`, `period_start/end`, `advance_date`) are `date` in the
  center's local timezone (`Asia/Kolkata`).

## 4. Key flows

### 4.1 Record a milk collection

```
POST /api/v1/collections  { clientTransactionId, producerNumber|qr, date, shift,
                            litres, fat, snf }
  1. Resolve producer (ProducerDirectory) → producer + region.
  2. RateResolutionService.resolve(region, date, fat, snf)
        pick ts_value: ACTIVE REGION rate_charts row covering the date, else COMMON,
        else -> RateNotConfigured error (unless center.allow_missing_rate).
        rate = (fat + snf) * ts_value / 100      (RateFormula)
  3. IncentiveCalculator.calculate(producer, region, date, litres)
        (common + individual) per center.incentive_stacking, then capped at
        center.incentive_max_per_litre (₹2.00).
  4. CollectionAmountCalculator.calculate(...)  → base, incentive, net (BigDecimal).
  5. Persist milk_collections (rate + incentive-rate + breakdown snapshot,
        client_transaction_id UNIQUE → duplicate returns the existing row).
  6. Generate collection_number; write collection_sync_log row; audit
        COLLECTION_CREATED; enqueue COLLECTION_CREATED notification (outbox).
  7. Return breakdown for the printable challan.
```

Offline: the PWA stores submissions locally with a `clientTransactionId`, replays
on reconnect; step 5's `UNIQUE` makes replays safe (returns existing → `DUPLICATE`).

### 4.2 Weekly settlement

```
POST /api/v1/settlements            { periodStart, periodEnd }   → DRAFT
POST /api/v1/settlements/{id}/calculate                          → CALCULATED
  For each producer with CONFIRMED, unsettled collections in range:
    line = SettlementCalculator.calculateLine(
              collections (rate snapshots as-collected),
              incentives  (already baked per collection),
              pendingDeductions (DeductionCalculator),
              advanceRecovery   (AdvanceRecoveryCalculator, capped at outstanding),
              adjustments,
              RoundingRule from center settings)
    persist settlement_items + snapshot; link collections / deduction txns /
    advance txns to the settlement_item.
  Roll up header totals; store calculation_snapshot + calc_engine_version.
POST /api/v1/settlements/{id}/review     (ACCOUNTS_USER)         → REVIEWED
POST /api/v1/settlements/{id}/approve    (CENTER_MANAGER/ADMIN)  → APPROVED   (frozen)
POST /api/v1/settlements/{id}/payouts    generate one payout per settlement_item → PAYOUT_INITIATED
  ... provider processing + webhooks ...                        → PARTIALLY_PAID / PAID / FAILED
```

After `APPROVED` a settlement is immutable. Corrections happen only through
`settlement_adjustments` applied to a **later** settlement (`origin_settlement_id`
points back at the one being corrected).

### 4.3 Payout

```
Per settlement_item:
  validate primary ACTIVE bank account → create payouts row (idempotency_key,
    provider = center-configured) → PayoutProvider.create(key, amount, bank).
  Store provider_payout_id/ref. Wait for webhook (verify signature).
  SUCCESS → utr, settled_at, settlement_item.payment_status=SUCCESS, notify.
  FAILED  → failure_code/reason; authorized retry creates a new attempt row
            in payout_transactions under the SAME payouts row / idempotency_key.
  Network timeout on create → NEVER auto-fail; reconcile via provider fetch.
```

## 5. Testing focus (design contract)

Every item in spec `testing.critical_test_cases` maps to a pure-calculator unit
test: `RateFormula` (`(fat+snf)*ts/100`) + regional/common `ts_value` fallback
(`RateResolutionService`), incentive stacking + the ₹2/L cap
(`IncentiveCalculator`), fixed-amount deduction totals (`DeductionCalculator`),
advance recovery `min(weekly, outstanding, max(net,0))` + outstanding never
negative (`AdvanceRecoveryCalculator`), weekly settlement math + banker's rounding
(`SettlementCalculator` + `RoundingRule`). Integration tests (Testcontainers +
PostgreSQL) cover the `UNIQUE`/`EXCLUDE` constraints: duplicate collection,
overlapping `ts_value` rows, overlapping settlement periods, duplicate payout.

## 6. Deferred to scaffold phase

Build files (`pom.xml`/Gradle), Spring config classes, entity classes,
repositories, controllers, React app, Docker/ECS/Terraform, CI. This phase
fixes the schema and the calculation contracts they depend on.
