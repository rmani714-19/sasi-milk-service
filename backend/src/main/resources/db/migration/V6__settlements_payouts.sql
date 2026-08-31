-- =====================================================================
-- V6  Settlements, settlement items, adjustments, payouts
-- =====================================================================

create table settlements (
    id                     bigint        generated always as identity primary key,
    public_id              uuid          not null default gen_random_uuid() unique,
    settlement_number      text          not null unique,
    period_start           date          not null,
    period_end             date          not null,
    status                 text          not null default 'DRAFT'
                           check (status in ('DRAFT','CALCULATED','REVIEWED','APPROVED',
                                             'PAYOUT_INITIATED','PARTIALLY_PAID','PAID','FAILED','CANCELLED')),

    total_producers        int           not null default 0,
    total_litres           numeric(14,2) not null default 0,
    total_milk_amount      numeric(16,2) not null default 0,
    total_incentive        numeric(16,2) not null default 0,
    total_deduction        numeric(16,2) not null default 0,
    total_advance_recovery numeric(16,2) not null default 0,
    total_adjustment       numeric(16,2) not null default 0,
    total_round_off        numeric(16,2) not null default 0,
    total_net_payable      numeric(16,2) not null default 0,

    -- rounding config snapshotted for reproducibility
    rounding_mode          text          not null,
    rounding_scale         int           not null,
    calc_engine_version    text,
    calculation_snapshot   jsonb         not null default '{}'::jsonb,

    calculated_at          timestamptz,
    calculated_by          bigint,
    reviewed_at            timestamptz,
    reviewed_by            bigint,
    approved_at            timestamptz,
    approved_by            bigint,
    cancelled_at           timestamptz,
    cancelled_by           bigint,
    version                bigint        not null default 0,
    created_at             timestamptz   not null default now(),
    created_by             bigint,
    updated_at             timestamptz   not null default now(),
    updated_by             bigint,
    constraint ck_settlement_period check (period_end >= period_start)
);

-- non-cancelled settlement periods must not overlap
alter table settlements add constraint ex_settlement_no_overlap
    exclude using gist (
        daterange(period_start, period_end, '[]') with &&
    ) where (status <> 'CANCELLED');

create table settlement_items (
    id                   bigint        generated always as identity primary key,
    settlement_id        bigint        not null references settlements(id),
    producer_id          bigint        not null references producers(id),
    region_id            bigint        references regions(id),
    bank_account_id      bigint        references producer_bank_accounts(id),

    morning_litres       numeric(12,2) not null default 0,
    evening_litres       numeric(12,2) not null default 0,
    total_litres         numeric(12,2) not null default 0,
    collection_count     int           not null default 0,
    average_rate         numeric(10,4) not null default 0,   -- litre-weighted (see OPEN_QUESTIONS #18)

    milk_amount          numeric(14,2) not null default 0,
    incentive_amount     numeric(14,2) not null default 0,
    gross_amount         numeric(14,2) not null default 0,

    feed_deduction       numeric(14,2) not null default 0,
    veterinary_deduction numeric(14,2) not null default 0,
    other_deduction      numeric(14,2) not null default 0,
    advance_recovery     numeric(14,2) not null default 0,
    total_deduction      numeric(14,2) not null default 0,

    adjustment_amount    numeric(14,2) not null default 0,   -- signed: +credit / -debit
    net_before_rounding  numeric(14,2) not null default 0,
    round_off            numeric(14,2) not null default 0,   -- signed
    net_payable          numeric(14,2) not null default 0,

    payment_status       text          not null default 'PENDING'
                         check (payment_status in ('PENDING','INITIATED','PROCESSING','SUCCESS','FAILED','ON_HOLD','CANCELLED')),
    payment_reference    text,
    calculation_snapshot jsonb         not null default '{}'::jsonb,
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now(),
    constraint ux_settlement_item unique (settlement_id, producer_id)
);
create index ix_settlement_item_producer  on settlement_items(producer_id);
create index ix_settlement_item_paystatus on settlement_items(payment_status);

create table settlement_adjustments (
    id                   bigint        generated always as identity primary key,
    settlement_id        bigint        references settlements(id),          -- settlement carrying the adjustment
    settlement_item_id   bigint        references settlement_items(id),
    producer_id          bigint        not null references producers(id),
    adjustment_type      text          not null check (adjustment_type in ('CREDIT','DEBIT')),
    amount               numeric(14,2) not null check (amount > 0),
    reason               text          not null,
    origin_settlement_id bigint        references settlements(id),          -- the (approved) settlement being corrected
    status               text          not null default 'ACTIVE' check (status in ('ACTIVE','REVERSED')),
    created_at           timestamptz   not null default now(),
    created_by           bigint
);
create index ix_settlement_adj_settlement on settlement_adjustments(settlement_id);
create index ix_settlement_adj_producer   on settlement_adjustments(producer_id);

-- ---------------------------------------------------------------------
-- wire the back-references now that settlement_items exists
-- ---------------------------------------------------------------------
alter table milk_collections
    add constraint fk_collection_settlement_item
    foreign key (settlement_item_id) references settlement_items(id);

alter table deduction_transactions
    add constraint fk_deduction_settlement_item
    foreign key (settlement_item_id) references settlement_items(id);

alter table advance_transactions
    add constraint fk_advtxn_settlement_item
    foreign key (settlement_item_id) references settlement_items(id);

-- ---------------------------------------------------------------------
-- payouts: exactly one per settlement_item; provider called once per key
-- ---------------------------------------------------------------------
create table payouts (
    id                 bigint        generated always as identity primary key,
    public_id          uuid          not null default gen_random_uuid() unique,
    settlement_item_id bigint        not null unique references settlement_items(id),  -- no duplicate disbursement
    settlement_id      bigint        not null references settlements(id),
    producer_id        bigint        not null references producers(id),
    bank_account_id    bigint        not null references producer_bank_accounts(id),
    amount             numeric(14,2) not null check (amount >= 0),
    currency_code      text          not null default 'INR',
    idempotency_key    text          not null unique,
    provider           text          not null,          -- RAZORPAYX, MANUAL, ...
    provider_payout_id text,
    provider_ref       text,
    utr                text,                             -- bank UTR on success
    status             text          not null default 'PENDING'
                       check (status in ('PENDING','INITIATED','PROCESSING','SUCCESS','FAILED','CANCELLED','REVERSED')),
    failure_code       text,
    failure_reason     text,
    attempt_count      int           not null default 0,
    last_attempt_at    timestamptz,
    settled_at         timestamptz,
    version            bigint        not null default 0,
    created_at         timestamptz   not null default now(),
    created_by         bigint,
    updated_at         timestamptz   not null default now(),
    updated_by         bigint
);
create index ix_payouts_settlement on payouts(settlement_id);
create index ix_payouts_status     on payouts(status);
create index ix_payouts_producer   on payouts(producer_id);

-- every request / response / webhook / retry / manual update / reconciliation
create table payout_transactions (
    id               bigint      generated always as identity primary key,
    payout_id        bigint      not null references payouts(id),
    event_type       text        not null
                     check (event_type in ('REQUEST','RESPONSE','WEBHOOK','RETRY','MANUAL_UPDATE','RECONCILIATION')),
    status           text,
    provider_ref     text,
    idempotency_key  text,
    http_status      int,
    request_payload  jsonb,
    response_payload jsonb,
    signature_valid  boolean,
    created_at       timestamptz not null default now()
);
create index ix_payout_txn_payout on payout_transactions(payout_id);
-- one processed webhook per (payout, provider_ref)
create unique index ux_payout_webhook_dedupe
    on payout_transactions(payout_id, provider_ref)
    where event_type = 'WEBHOOK' and provider_ref is not null;
