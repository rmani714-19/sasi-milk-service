-- =====================================================================
-- V5  Deduction types & transactions, advance accounts & ledger
-- =====================================================================

create table deduction_types (
    id             bigint generated always as identity primary key,
    code           text        not null unique,   -- FEED, VETERINARY, LOAN, EQUIPMENT, PENALTY, OTHER, ADVANCE_RECOVERY
    name           text        not null,
    is_system      boolean     not null default false,
    affects_ledger boolean     not null default false,  -- true when it carries an outstanding balance
    status         text        not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    created_at     timestamptz not null default now()
);

-- Deductions are always a FIXED rupee amount (client, OQ #6: "not percentage").
-- Typical use: cow feed, emergency cash the producer collected during the week.
-- (Advance recovery is NOT a deduction row — see advance_accounts / OQ #7.)
create table deduction_transactions (
    id                bigint generated always as identity primary key,
    producer_id       bigint        not null references producers(id),
    deduction_type_id bigint        not null references deduction_types(id),
    amount            numeric(14,2) not null check (amount >= 0),
    txn_date          date          not null default current_date,
    description       text,
    status            text          not null default 'PENDING'
                      check (status in ('PENDING','APPLIED','CANCELLED')),
    settlement_item_id bigint,      -- FK added in V6
    reversal_of_id    bigint        references deduction_transactions(id),
    version           bigint        not null default 0,
    created_at        timestamptz   not null default now(),
    created_by        bigint,
    updated_at        timestamptz   not null default now(),
    updated_by        bigint
);
create index ix_deduction_producer on deduction_transactions(producer_id);
create index ix_deduction_pending  on deduction_transactions(producer_id) where status = 'PENDING';

-- ---------------------------------------------------------------------
-- advance accounts: one per advance grant; outstanding tracked here
-- ---------------------------------------------------------------------
create table advance_accounts (
    id                  bigint        generated always as identity primary key,
    producer_id         bigint        not null references producers(id),
    advance_date        date          not null default current_date,
    original_amount     numeric(14,2) not null check (original_amount > 0),
    recovery_mode       text          not null check (recovery_mode in ('FIXED_PER_WEEK','PERCENTAGE_OF_NET')),
    recovery_amount     numeric(14,2) check (recovery_amount is null or recovery_amount > 0),
    recovery_percentage numeric(6,3)  check (recovery_percentage is null or (recovery_percentage > 0 and recovery_percentage <= 100)),
    outstanding_amount  numeric(14,2) not null check (outstanding_amount >= 0),  -- never negative
    status              text          not null default 'ACTIVE'
                        check (status in ('ACTIVE','CLOSED','CANCELLED')),
    description         text,
    version             bigint        not null default 0,
    created_at          timestamptz   not null default now(),
    created_by          bigint,
    updated_at          timestamptz   not null default now(),
    updated_by          bigint,
    closed_at           timestamptz,
    constraint ck_advance_recovery
        check ((recovery_mode = 'FIXED_PER_WEEK'    and recovery_amount is not null)
            or (recovery_mode = 'PERCENTAGE_OF_NET' and recovery_percentage is not null))
);
create index ix_advance_producer_active on advance_accounts(producer_id) where status = 'ACTIVE';

-- append-only ledger: every disbursement / recovery / adjustment is one row
create table advance_transactions (
    id                 bigint        generated always as identity primary key,
    advance_account_id bigint        not null references advance_accounts(id),
    producer_id        bigint        not null references producers(id),
    txn_type           text          not null
                       check (txn_type in ('DISBURSEMENT','RECOVERY','ADJUSTMENT','WRITE_OFF','REVERSAL')),
    amount             numeric(14,2) not null check (amount <> 0),   -- sign convention handled in app layer
    balance_after      numeric(14,2) not null check (balance_after >= 0),  -- over-recovery impossible
    txn_date           date          not null default current_date,
    settlement_item_id bigint,       -- FK added in V6 (set for RECOVERY rows)
    reference          text,
    reversal_of_id     bigint        references advance_transactions(id),
    created_at         timestamptz   not null default now(),
    created_by         bigint
);
create index ix_advance_txn_account  on advance_transactions(advance_account_id);
create index ix_advance_txn_producer on advance_transactions(producer_id);
