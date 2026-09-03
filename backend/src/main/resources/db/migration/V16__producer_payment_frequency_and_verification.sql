-- =====================================================================
-- V16  Producer-specific payment frequency + per-producer payment verification
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Producer payment frequency  (WEEKLY | MONTHLY) — required, defaults WEEKLY
-- ---------------------------------------------------------------------
alter table producers
    add column payment_frequency text not null default 'WEEKLY'
        check (payment_frequency in ('WEEKLY', 'MONTHLY'));

create index ix_producers_payment_frequency on producers(payment_frequency);

-- ---------------------------------------------------------------------
-- 2. Settlement run scope: which producers a run pays
--    WEEKLY  -> only producers on weekly frequency
--    MONTHLY -> only producers on monthly frequency
--    ALL     -> legacy behaviour (every eligible producer, no filter)
-- ---------------------------------------------------------------------
alter table settlements
    add column payment_frequency text not null default 'ALL'
        check (payment_frequency in ('WEEKLY', 'MONTHLY', 'ALL'));

-- A weekly run and a monthly run naturally cover overlapping calendar ranges,
-- so the no-overlap rule now only applies within the same scope.
alter table settlements drop constraint ex_settlement_no_overlap;
alter table settlements add constraint ex_settlement_no_overlap
    exclude using gist (
        payment_frequency with =,
        daterange(period_start, period_end, '[]') with &&
    ) where (status <> 'CANCELLED');

-- ---------------------------------------------------------------------
-- 3. Settlement lifecycle gains explicit verification states
--    DRAFT -> CALCULATED -> UNDER_VERIFICATION -> VERIFIED -> APPROVED -> payout...
--    (REVIEWED kept for the legacy ALL-scope flow)
-- ---------------------------------------------------------------------
alter table settlements drop constraint settlements_status_check;
alter table settlements add constraint settlements_status_check
    check (status in ('DRAFT', 'CALCULATED', 'REVIEWED', 'UNDER_VERIFICATION', 'VERIFIED', 'APPROVED',
                      'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED', 'CANCELLED'));

-- ---------------------------------------------------------------------
-- 4. Per-producer verification on the settlement line
--    verified_amount snapshots net_payable at verify time; if a later
--    recalculation moves net_payable, approval is blocked until re-verified.
-- ---------------------------------------------------------------------
alter table settlement_items
    add column verification_status text not null default 'PENDING_VERIFICATION'
        check (verification_status in ('PENDING_VERIFICATION', 'VERIFIED')),
    add column verified_amount numeric(14, 2),
    add column verified_by     bigint,
    add column verified_at     timestamptz;

create index ix_settlement_item_verification on settlement_items(verification_status);
