-- =====================================================================
-- V4  Milk collections, quality (QC) extension, offline sync log
-- =====================================================================

create table milk_collections (
    id                     bigint generated always as identity primary key,
    public_id              uuid          not null default gen_random_uuid() unique,
    client_transaction_id  uuid          not null unique,   -- idempotency / offline replay
    collection_number      text          not null unique,   -- human readable challan no.
    producer_id            bigint        not null references producers(id),
    region_id              bigint        references regions(id),  -- snapshot of producer's region

    collection_date        date          not null,
    shift                  text          not null references shifts(code),

    milk_quantity_litres   numeric(10,2) not null check (milk_quantity_litres > 0),
    fat_percentage         numeric(4,2)  not null check (fat_percentage >= 0),
    snf_percentage         numeric(4,2)  not null check (snf_percentage >= 0),

    -- rate snapshot: captured at collection time, NEVER recomputed.
    -- applicable_rate = (fat_percentage + snf_percentage) * ts_value_applied / 100
    rate_chart_id          bigint        references rate_charts(id),
    ts_value_applied       numeric(10,4) not null check (ts_value_applied >= 0),
    rate_scope_applied     text          check (rate_scope_applied in ('COMMON','REGION')),
    applicable_rate        numeric(10,4) not null check (applicable_rate >= 0),
    incentive_rate_applied numeric(10,4) not null default 0 check (incentive_rate_applied >= 0),

    base_amount            numeric(14,2) not null check (base_amount >= 0),   -- litres * applicable_rate
    incentive_amount       numeric(14,2) not null default 0 check (incentive_amount >= 0),
    deduction_amount       numeric(14,2) not null default 0 check (deduction_amount >= 0), -- point-of-collection only
    net_amount             numeric(14,2) not null,

    calculation_snapshot   jsonb         not null default '{}'::jsonb,

    status                 text          not null default 'CONFIRMED'
                           check (status in ('DRAFT','CONFIRMED','VOID')),
    settlement_item_id     bigint,       -- set when swept into a settlement (FK added in V6)
    source                 text          not null default 'ONLINE'
                           check (source in ('ONLINE','OFFLINE_SYNC','IMPORT')),
    void_reason            text,
    voided_at              timestamptz,
    voided_by              bigint,
    version                bigint        not null default 0,
    created_at             timestamptz   not null default now(),
    created_by             bigint,
    updated_at             timestamptz   not null default now(),
    updated_by             bigint
);
create index ix_collections_producer_date on milk_collections(producer_id, collection_date);
create index ix_collections_date_shift    on milk_collections(collection_date, shift);
create index ix_collections_region        on milk_collections(region_id);
create index ix_collections_unsettled     on milk_collections(collection_date)
    where status = 'CONFIRMED' and settlement_item_id is null;

-- optional lab / quality-control extension (1:1 with a collection)
create table milk_quality (
    id                bigint generated always as identity primary key,
    collection_id     bigint       not null unique references milk_collections(id),
    clr               numeric(5,2),                 -- corrected lactometer reading
    temperature_c     numeric(5,2),
    total_solids_pct  numeric(5,2),                 -- "TS" — semantics TBD with client (see OPEN_QUESTIONS #2)
    water_pct         numeric(5,2),
    adulteration_flag boolean      not null default false,
    remarks           text,
    recorded_at       timestamptz  not null default now(),
    recorded_by       bigint
);

-- raw offline submissions: kept for replay + audit, independent of dedupe outcome
create table collection_sync_log (
    id                    bigint generated always as identity primary key,
    client_transaction_id uuid        not null,
    device_id             text,
    received_at           timestamptz not null default now(),
    payload               jsonb       not null,
    outcome               text        not null check (outcome in ('APPLIED','DUPLICATE','REJECTED')),
    collection_id         bigint      references milk_collections(id),
    error_detail          text
);
create index ix_sync_log_ctid on collection_sync_log(client_transaction_id);
