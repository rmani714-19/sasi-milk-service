-- =====================================================================
-- V17  Incentives catalog — managed incentive definitions with an
--      effective period, status lifecycle and safe delete.
--
--      This is a management catalog. It does NOT change the existing
--      collection-time incentive engine (incentive_rules /
--      producer_incentives), rate calculation or settlement math.
-- =====================================================================

create table incentives (
    id             bigint      generated always as identity primary key,
    public_id      uuid        not null default gen_random_uuid() unique,

    name           text        not null,
    target         text        not null check (target in ('PRODUCER', 'MEDIATOR')),
    incentive_type text        not null check (incentive_type in (
                       'PER_LITRE', 'FIXED_AMOUNT', 'VOLUME_BONUS', 'DAILY_BONUS',
                       'WEEKLY_BONUS', 'MONTHLY_BONUS', 'RETENTION_BONUS',
                       'NEW_PRODUCER_BONUS', 'COMPETITOR_RETENTION')),
    amount         numeric(12, 4) not null check (amount >= 0),

    -- inclusive effective period; every incentive must have both dates
    effective_from date        not null,
    effective_to   date        not null,

    -- stored status is only the manual switch; EXPIRED is derived from
    -- effective_to < current_date and is never persisted.
    status         text        not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),

    description    text,
    priority       int         not null default 0,

    activated_at   timestamptz,
    activated_by   bigint,
    deactivated_at timestamptz,
    deactivated_by bigint,

    version        bigint      not null default 0,
    created_at     timestamptz not null default now(),
    created_by     bigint,
    updated_at     timestamptz not null default now(),
    updated_by     bigint,

    constraint ck_incentive_period check (effective_to >= effective_from)
);

create index ix_incentives_status    on incentives(status);
create index ix_incentives_target    on incentives(target);
create index ix_incentives_effective on incentives(effective_from, effective_to);
create index ix_incentives_created   on incentives(created_at desc);
