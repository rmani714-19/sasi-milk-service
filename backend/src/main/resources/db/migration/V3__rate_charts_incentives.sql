-- =====================================================================
-- V3  Rate charts (formula coefficient, effective-dated) + incentives
-- =====================================================================
--
-- The milk rate is a FORMULA, not a FAT/SNF lookup matrix (client, OQ #1/#3):
--
--     rate_per_litre = (fat_percentage + snf_percentage) * ts_value / 100
--
-- A "rate chart" therefore holds one coefficient (ts_value) for a scope and an
-- effective date range. Region resolution: use the REGION chart covering the
-- collection date if one exists, else the COMMON chart, else fail (OQ #3).
-- The spec's `rate_chart_entries` matrix table is intentionally not created.

create table rate_charts (
    id             bigint generated always as identity primary key,
    scope          text        not null check (scope in ('COMMON','REGION')),
    region_id      bigint      references regions(id),
    name           text,
    ts_value       numeric(10,4) not null check (ts_value >= 0),   -- formula coefficient
    effective_from date        not null,
    effective_to   date,                            -- null = open ended
    status         text        not null default 'ACTIVE'
                   check (status in ('DRAFT','ACTIVE','ARCHIVED')),
    note           text,
    version        bigint      not null default 0,
    created_at     timestamptz not null default now(),
    created_by     bigint,
    updated_at     timestamptz not null default now(),
    updated_by     bigint,
    constraint ck_rate_chart_region
        check ((scope = 'REGION' and region_id is not null)
            or (scope = 'COMMON' and region_id is null)),
    constraint ck_rate_chart_dates
        check (effective_to is null or effective_to >= effective_from)
);

-- no two ACTIVE charts for the same scope/region may overlap in time.
-- a NULL effective_to yields a range unbounded above.
alter table rate_charts add constraint ex_rate_chart_no_overlap
    exclude using gist (
        scope with =,
        coalesce(region_id, 0) with =,
        daterange(effective_from, effective_to, '[]') with &&
    ) where (status = 'ACTIVE');

create index ix_rate_charts_lookup
    on rate_charts(scope, region_id, effective_from) where status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- COMMON incentives (optionally region-scoped), effective-dated, per litre
-- ---------------------------------------------------------------------
create table incentive_rules (
    id                       bigint generated always as identity primary key,
    scope                    text        not null default 'COMMON'
                             check (scope in ('COMMON','REGION')),
    region_id                bigint      references regions(id),
    incentive_rate_per_litre numeric(10,4) not null check (incentive_rate_per_litre >= 0),
    effective_date           date        not null,
    end_date                 date,
    description              text,
    status                   text        not null default 'ACTIVE'
                             check (status in ('ACTIVE','CANCELLED')),
    version                  bigint      not null default 0,
    created_at               timestamptz not null default now(),
    created_by               bigint,
    updated_at               timestamptz not null default now(),
    updated_by               bigint,
    cancelled_at             timestamptz,
    cancelled_by             bigint,
    constraint ck_incentive_rule_region
        check ((scope = 'REGION' and region_id is not null)
            or (scope = 'COMMON' and region_id is null)),
    constraint ck_incentive_rule_dates
        check (end_date is null or end_date >= effective_date)
);
create index ix_incentive_rule_active
    on incentive_rules(scope, region_id, effective_date) where status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- INDIVIDUAL producer incentives, effective-dated, per litre
-- ---------------------------------------------------------------------
create table producer_incentives (
    id                       bigint generated always as identity primary key,
    producer_id              bigint      not null references producers(id),
    incentive_rate_per_litre numeric(10,4) not null check (incentive_rate_per_litre >= 0),
    effective_date           date        not null,
    end_date                 date,
    description              text,
    status                   text        not null default 'ACTIVE'
                             check (status in ('ACTIVE','CANCELLED')),
    version                  bigint      not null default 0,
    created_at               timestamptz not null default now(),
    created_by               bigint,
    updated_at               timestamptz not null default now(),
    updated_by               bigint,
    cancelled_at             timestamptz,
    cancelled_by             bigint,
    constraint ck_prod_incentive_dates
        check (end_date is null or end_date >= effective_date)
);
create index ix_prod_incentive_producer
    on producer_incentives(producer_id, effective_date) where status = 'ACTIVE';
