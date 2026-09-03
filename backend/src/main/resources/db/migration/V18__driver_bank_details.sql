-- =====================================================================
-- V18  Driver bank details — payout information for monthly salary.
--      The account number is AES-GCM encrypted at rest (like producer
--      bank accounts); last4 + blind-index hash support masked display
--      and change detection without decryption.
--
--      Columns are nullable so the existing driver rows survive; the API
--      requires a complete bank block on create/edit and salary payment
--      by BANK_TRANSFER is blocked when any required field is missing.
-- =====================================================================

alter table drivers
    add column account_holder_name  text,
    add column account_number_enc   text,
    add column account_number_last4 text,
    add column account_number_hash  text,
    add column ifsc_code            text,
    add column bank_name            text,
    add column branch_name          text,
    add column bank_updated_at      timestamptz,
    add column bank_updated_by      bigint;
