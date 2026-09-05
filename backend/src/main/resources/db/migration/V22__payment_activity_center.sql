-- =====================================================================
-- V22  Payment Activity Center — a place to see every outgoing payment
--      (farmer settlement payouts, driver salaries, and now misc business
--      expenses) in one list, flag one for follow-up if something looks
--      wrong, and resolve the flag once it's sorted out.
-- =====================================================================

-- One flag can be raised against a payout, a driver-salary line, or an
-- expense. At most one OPEN flag per subject at a time; raising again
-- after a resolve starts a fresh row, so the full dispute history survives.
create table payment_flags (
    id               bigint      generated always as identity primary key,
    public_id        uuid        not null default gen_random_uuid() unique,

    subject_type     text        not null check (subject_type in ('PAYOUT', 'SALARY_ITEM', 'EXPENSE')),
    subject_id       bigint      not null,

    note             text        not null,
    status           text        not null default 'OPEN' check (status in ('OPEN', 'RESOLVED')),

    flagged_by       bigint,
    flagged_at       timestamptz not null default now(),

    resolution_note  text,
    resolved_by      bigint,
    resolved_at      timestamptz,

    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    created_by       bigint,
    updated_at       timestamptz not null default now(),
    updated_by       bigint
);

create index ix_payment_flag_subject on payment_flags(subject_type, subject_id, id desc);
create unique index uq_payment_flag_open on payment_flags(subject_type, subject_id) where status = 'OPEN';

-- Misc business expenses (fuel, rent, maintenance, ...) paid from the same
-- business account as farmer/driver payments — logged here so they show up
-- alongside those in the unified Payment Activity view.
create sequence expense_number_seq start with 1;

create table expenses (
    id                bigint      generated always as identity primary key,
    public_id         uuid        not null default gen_random_uuid() unique,
    expense_number    text        not null unique,

    category          text        not null check (category in
                       ('FUEL', 'MAINTENANCE', 'RENT', 'UTILITIES', 'OFFICE_SUPPLIES',
                        'TRANSPORT', 'SALARY_OTHER', 'MISC', 'OTHER')),
    description       text,
    amount            numeric(12, 2) not null check (amount > 0),
    expense_date      date        not null,

    payment_method    text        not null default 'BANK_TRANSFER'
                       check (payment_method in ('CASH', 'BANK_TRANSFER', 'UPI', 'CHEQUE', 'OTHER')),
    payment_reference text,
    status            text        not null default 'PAID' check (status in ('PAID', 'FAILED', 'CANCELLED')),
    paid_at           timestamptz not null default now(),

    version           bigint      not null default 0,
    created_at        timestamptz not null default now(),
    created_by        bigint,
    updated_at        timestamptz not null default now(),
    updated_by        bigint
);

create index ix_expense_date on expenses(expense_date desc, id desc);
