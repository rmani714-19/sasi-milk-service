# Business questions — client answers of 2026-08-31

Answered items are **RESOLVED** below with the decision as-built and any residual
sub-question. Everything stays configurable via the `center` table so a later
change needs no rewrite.

## Resolved

| # | Question | Client answer | As built | Residual to confirm |
|---|---|---|---|---|
| 1 | FAT/SNF → rate | **`rate/litre = (FAT% + SNF%) × TS_Value ÷ 100`** — a formula, not a matrix | `RateFormula`; `rate_charts.ts_value` (effective-dated); `rate_chart_entries` table removed; `milk_collections.ts_value_applied` snapshot | Typical `TS_Value` magnitude + one worked example from a real challan, to lock a unit test |
| 3 | Regional vs common rate | Same formula; region differs only by its **TS_Value** | REGION `rate_charts` row overrides COMMON for the date, else COMMON, else `RateNotConfiguredException` | — |
| 4 | Individual vs common incentive | Flat ₹/litre, **max ₹2, set by Admin** | `incentive_rules` (common) + `producer_incentives` (individual) combined per `center.incentive_stacking` (default `ADD`), then capped at `center.incentive_max_per_litre = 2.00` | Do common + individual **add** (then cap), or does individual **replace** common? Default is ADD+cap |
| 5 | Deduction types | "Amount they collect for their emergency and cow feed" | Seeded `FEED`, `VETERINARY`, `EMERGENCY`, `OTHER`; advance recovery is its own ledger, not a deduction type | Is `VETERINARY` actually used, or fold into `OTHER`? |
| 6 | Percentage deduction base | **Not percentage** — fixed amounts (cow feed etc.) | `deduction_transactions` is a fixed `amount` only; percentage mode + `center.percentage_deduction_base` removed | — |
| 7 | Advance recovery | ₹10k advance, fixed **₹500/week**; earns ₹1.5k → paid ₹1k, ₹500 recovered | `advance_accounts.recovery_mode = FIXED_PER_WEEK`, `recovery_amount`; recover `min(recovery_amount, outstanding, max(net,0))`; ledger `balance_after ≥ 0` | Behaviour when weekly net < ₹500: recover the partial net, or skip and carry over? Default = recover partial |
| 9 | Rounding of final payable | **"Round as per banking"** | `center.rounding_mode = HALF_EVEN`, `rounding_scale = 0` (nearest whole rupee); signed `round_off` stored per line | Confirm banker's rounding (HALF_EVEN) vs plain nearest-rupee (HALF_UP) |

## Still open

| # | Question | Where it bites | Current default |
|---|---|---|---|
| 2 | Meaning of **TS / Total Solids** as a milk-quality reading (distinct from the `TS_Value` rate coefficient in #1) | `milk_quality.total_solids_pct` | stored as a QC field only |
| 8 | Settlement week — which day is week start? | `center.settlement_day_of_week` | Monday (ISO week) |
| 10 | Multiple collections per producer per shift? | `milk_collections` constraints | allowed; dedupe only via `client_transaction_id` |
| 11 | Edit a collection after save? | `milk_collections.status` | void + re-enter (audited); no in-place financial edit |
| 12 | Who approves a settlement? | `@PreAuthorize` | `APPROVE_SETTLEMENT` → CENTER_MANAGER + ADMIN |
| 13 | Payout provider + webhook signature scheme | `payout` | decided: none — payments are manual via the bank; the app only records them |
| 14 | SMS provider + India DLT; WhatsApp BSP; email provider | `notification_channel_config` | pluggable; DLT/WA template id per template |
| 15 | Post-approval corrections — next settlement only, or off-cycle payout? | `settlement_adjustments` | roll into next settlement via `origin_settlement_id` |
| 16 | Legacy MySQL export + field mapping | migration scripts (later) | pending |
| 17 | Receipt printer type | frontend receipt component | 80mm thermal + A5 fallback |
| 18 | Report average rate — litre-weighted vs simple mean | `settlement_items.average_rate` | litre-weighted |
