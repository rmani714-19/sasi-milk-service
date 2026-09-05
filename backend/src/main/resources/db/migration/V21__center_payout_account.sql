-- =====================================================================
-- V21  Centre's own payout bank account — configured once, reused by
--      every weekly/monthly farmer payment and driver-salary bank-balance
--      verification instead of being retyped each run.
--
--      This is a business-facing convenience record. It does not change
--      which account RazorpayX actually debits (sasi.payout.razorpayx.
--      account-number, server-side config) — see docs/RAZORPAYX.md.
-- =====================================================================

alter table center
    add column payout_account_holder_name  text,
    add column payout_account_number_enc   text,
    add column payout_account_number_last4 text,
    add column payout_account_number_hash  text,
    add column payout_ifsc                 text,
    add column payout_bank_name            text,
    add column payout_account_updated_at   timestamptz,
    add column payout_account_updated_by   bigint;
