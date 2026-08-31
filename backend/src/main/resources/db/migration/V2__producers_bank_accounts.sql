-- =====================================================================
-- V2  Producers, region history, bank accounts
-- =====================================================================

create table producers (
    id              bigint generated always as identity primary key,
    public_id       uuid        not null default gen_random_uuid() unique,
    producer_number text        not null unique,
    producer_name   text        not null,
    mobile_number   text,
    email           text,
    address         text,
    region_id       bigint      references regions(id),
    joining_date    date        not null default current_date,
    status          text        not null default 'ACTIVE'
                    check (status in ('ACTIVE','INACTIVE')),
    qr_code         text        not null unique,   -- opaque token encoded into the QR image
    notes           text,
    version         bigint      not null default 0,
    created_at      timestamptz not null default now(),
    created_by      bigint,
    updated_at      timestamptz not null default now(),
    updated_by      bigint
);
create index ix_producers_region on producers(region_id);
create index ix_producers_status on producers(status);

-- region change history ("change producer region with audit history")
create table producer_region_history (
    id            bigint generated always as identity primary key,
    producer_id   bigint      not null references producers(id),
    old_region_id bigint      references regions(id),
    new_region_id bigint      references regions(id),
    changed_at    timestamptz not null default now(),
    changed_by    bigint,
    reason        text
);
create index ix_prod_region_hist_producer on producer_region_history(producer_id);

-- bank accounts: number encrypted at app layer; only last4 + hash stored in clear
create table producer_bank_accounts (
    id                   bigint generated always as identity primary key,
    producer_id          bigint      not null references producers(id),
    account_holder_name  text        not null,
    account_number_enc   text        not null,       -- app-layer encrypted (AES-GCM, key in Secrets Manager)
    account_number_last4 text        not null,       -- masked display / search
    account_number_hash  text        not null,       -- deterministic HMAC for dedupe / lookup
    ifsc_code            text        not null,
    bank_name            text,
    bank_branch          text,
    is_primary           boolean     not null default true,
    status               text        not null default 'ACTIVE'
                         check (status in ('ACTIVE','INACTIVE')),
    verified_at          timestamptz,
    verified_by          bigint,
    version              bigint      not null default 0,
    created_at           timestamptz not null default now(),
    created_by           bigint,
    updated_at           timestamptz not null default now(),
    updated_by           bigint
);
-- exactly one active primary account per producer
create unique index ux_producer_primary_bank
    on producer_bank_accounts(producer_id)
    where is_primary and status = 'ACTIVE';
create index ix_producer_bank_producer on producer_bank_accounts(producer_id);
create index ix_producer_bank_hash on producer_bank_accounts(account_number_hash);
