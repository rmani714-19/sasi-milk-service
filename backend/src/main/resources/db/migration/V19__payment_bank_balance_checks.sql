-- =====================================================================
-- V19  Owner bank-balance verification for a payment batch.
--
--      One guided step between "all recipients verified" and "owner
--      approval": the accountant records the owner's available bank
--      balance (manual entry, or from a provider when integrated) and the
--      system checks it covers the batch. Approval of a farmer settlement
--      or a driver-salary batch is blocked until a SUFFICIENT check
--      exists. History is kept — the batch reads the latest row.
-- =====================================================================

create table payment_bank_balance_checks (
    id                 bigint      generated always as identity primary key,
    public_id          uuid        not null default gen_random_uuid() unique,

    subject_type       text        not null check (subject_type in ('SETTLEMENT', 'SALARY_BATCH')),
    subject_id         bigint      not null,

    bank_account_label text        not null,
    available_balance  numeric(16, 2) not null check (available_balance >= 0),
    required_amount    numeric(16, 2) not null check (required_amount >= 0),
    remaining_after    numeric(16, 2) not null,
    sufficient         boolean     not null,
    method             text        not null default 'MANUAL' check (method in ('MANUAL', 'AUTOMATIC')),
    note               text,

    verified_by        bigint,
    verified_at        timestamptz not null default now(),

    version            bigint      not null default 0,
    created_at         timestamptz not null default now(),
    created_by         bigint,
    updated_at         timestamptz not null default now(),
    updated_by         bigint
);

create index ix_bank_balance_check_subject
    on payment_bank_balance_checks(subject_type, subject_id, id desc);
