# Data model notes

PostgreSQL 17 (validated). Migrations:
`backend/src/main/resources/db/migration/V1__*.sql` … `V10__*.sql` (Flyway, run in
order) — **33 base tables**. 29 of the spec's 30 `database_design.tables` are
present; `rate_chart_entries` is intentionally omitted (the rate is a formula, not
a matrix — see below). Four operational support tables are added and noted below.

## Milk rate — formula, not a matrix (client-confirmed)

```
rate_per_litre = (fat_percentage + snf_percentage) * ts_value / 100
```

`ts_value` is an effective-dated coefficient on `rate_charts`: one `COMMON` row,
plus optional per-`REGION` rows that override it for producers in that region.
Resolution: REGION row covering the date → else COMMON row → else error (unless
`center.allow_missing_rate`). The applied `ts_value` and resulting rate are
snapshotted onto every `milk_collections` row and never recomputed.

## Numeric scales (fixed, never `double`)

| Concept | Type | Rationale |
|---|---|---|
| Money (line amounts) | `NUMERIC(14,2)` | ≤ 999,999,999,999.99 |
| Money (settlement roll-ups) | `NUMERIC(16,2)` | headroom for center-wide totals |
| Milk quantity (litres) | `NUMERIC(10,2)` | per-collection & aggregates |
| FAT % / SNF % | `NUMERIC(4,2)` | e.g. `3.50`, `8.60` |
| Rate per litre | `NUMERIC(10,4)` | computed `(FAT%+SNF%)×TS_Value÷100` |
| `rate_charts.ts_value` (formula coefficient) | `NUMERIC(10,4)` | admin-set; ~250–320 in practice |
| Incentive rate per litre | `NUMERIC(10,4)` | flat ₹/L, capped at `center.incentive_max_per_litre` |
| Advance `recovery_percentage` | `NUMERIC(6,3)` | `0.000`–`100.000` (fixed-per-week is the confirmed mode) |
| Lat / Lng | `NUMERIC(9,6)` | ~0.1 m precision |

Rounding is **not** applied at storage time except where a column explicitly
represents a rounded value (`settlement_items.net_payable`,
`settlements.total_net_payable`). Rounding mode + scale come from `center` and are
copied onto `settlements` for reproducibility.

## Identity & keys

- Surrogate PK: `bigint generated always as identity` everywhere.
- `public_id uuid` (UNIQUE) on externally-referenced aggregates: `users`,
  `producers`, `milk_collections`, `settlements`, `payouts`. Use in URLs / QR /
  API payloads so internal ids are not enumerable.
- Business-unique keys: `producers.producer_number`, `roles.code`,
  `regions.code`, `milk_collections.collection_number`,
  `settlements.settlement_number`.

## Constraints that encode business rules

| Constraint | Table | Rule enforced |
|---|---|---|
| `client_transaction_id UNIQUE` | `milk_collections` | duplicate / offline-replay safe |
| `ex_rate_chart_no_overlap` (GiST EXCLUDE) | `rate_charts` | no two ACTIVE `ts_value` rows overlap for the same scope/region |
| `ck_incentive_*` | `incentive_rules` / `producer_incentives` | COMMON has no producer; INDIVIDUAL has one; `end_date >= effective_date` |
| `ck_advance_recovery`, `outstanding_amount >= 0` | `advance_accounts` | recovery config consistent; outstanding never negative |
| `balance_after >= 0` | `advance_transactions` | over-recovery impossible at the ledger |
| `ex_settlement_no_overlap` (GiST EXCLUDE) | `settlements` | non-cancelled settlement periods cannot overlap |
| `ux_settlement_item` | `settlement_items` | one line per producer per settlement |
| `settlement_item_id UNIQUE` | `payouts` | at most one payout per settlement line — no duplicate disbursement |
| `idempotency_key UNIQUE` | `payouts` | provider called at-most-once per key |
| `dedupe_key UNIQUE` | `notifications` | no duplicate sends |
| `ux_payout_webhook_dedupe` | `payout_transactions` | one processed webhook per (payout, provider_ref) |

Requires extensions: `pgcrypto` (`gen_random_uuid()`), `btree_gist` (EXCLUDE
constraints). Created in `V1`.

## Snapshots

- `milk_collections`: `ts_value_applied`, `applicable_rate`,
  `incentive_rate_applied`, `rate_chart_id`, `rate_scope_applied`,
  `calculation_snapshot jsonb`.
- `settlements` / `settlement_items`: every component column **plus**
  `calculation_snapshot jsonb` and `settlements.calc_engine_version`.
- Rounding: `settlements.rounding_mode` = `HALF_EVEN` ("as per banking"),
  `rounding_scale` = `0` (nearest whole rupee); each `settlement_items.round_off`
  records the signed adjustment.

## Support tables added beyond the spec list (operational, not business scope changes)

| Table | Why |
|---|---|
| `refresh_tokens` | refresh-token rotation (spec security requirement, no table was listed) |
| `producer_region_history` | "change producer region with audit history" |
| `collection_sync_log` | raw offline submissions for replay + audit |
| `notification_channel_config` | per-channel enable/sender config (secrets stay in AWS Secrets Manager) |

## Seed data (`V10`) — structural / reference only

Roles + permission arrays (from spec `user_roles`), `shifts` (MORNING, EVENING),
`regions` REGION_1..REGION_4, `deduction_types` (FEED, VETERINARY, EMERGENCY,
OTHER), one `center` row with default config (`HALF_EVEN`/scale 0 rounding,
`incentive_max_per_litre` 2.00), and `notification_templates` seeded from the
spec's provided message text (editable by admins). **No** producers, **no**
`ts_value` rate rows, **no** incentives are seeded — those are business data. An
admin must create a COMMON `rate_charts` row before the first collection can be
priced.
