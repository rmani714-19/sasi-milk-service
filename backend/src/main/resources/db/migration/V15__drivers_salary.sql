-- =====================================================================
-- V15  Drivers + monthly driver-salary payments
--      Farmer milk payments continue to use settlements + payouts (V6);
--      this only adds the driver-salary side of the Payments module.
-- =====================================================================

create table drivers (
    id             bigint        generated always as identity primary key,
    code           text          not null unique,
    name           text          not null,
    mobile         text,
    joining_date   date,
    vehicle_id     bigint        references vehicles(id),
    salary_type    text          not null default 'FIXED'
                   check (salary_type in ('FIXED','PER_DAY','CUSTOM')),
    monthly_salary numeric(12,2) not null default 0 check (monthly_salary >= 0),
    per_day_rate   numeric(12,2) check (per_day_rate is null or per_day_rate >= 0),
    status         text          not null default 'ACTIVE'
                   check (status in ('ACTIVE','INACTIVE')),
    note           text,
    version        bigint        not null default 0,
    created_at     timestamptz   not null default now(),
    created_by     bigint,
    updated_at     timestamptz   not null default now(),
    updated_by     bigint
);
create index ix_drivers_status on drivers(status);

create sequence driver_number_seq       start with 1 increment by 1;
create sequence salary_batch_number_seq start with 1 increment by 1;

create table driver_salary_batches (
    id               bigint        generated always as identity primary key,
    public_id        uuid          not null default gen_random_uuid() unique,
    batch_number     text          not null unique,
    batch_type       text          not null default 'DRIVER_MONTHLY'
                     check (batch_type in ('DRIVER_MONTHLY')),
    period_month     date          not null,          -- first day of the salary month
    status           text          not null default 'DRAFT'
                     check (status in ('DRAFT','CALCULATED','REVIEWED','APPROVED',
                                       'PAYMENT_INITIATED','PARTIALLY_PAID','PAID','FAILED','CANCELLED')),

    total_drivers    int           not null default 0,
    total_gross      numeric(16,2) not null default 0,
    total_allowances numeric(16,2) not null default 0,
    total_overtime   numeric(16,2) not null default 0,
    total_deductions numeric(16,2) not null default 0,
    total_net        numeric(16,2) not null default 0,
    total_paid       numeric(16,2) not null default 0,

    calculated_at    timestamptz, calculated_by bigint,
    reviewed_at      timestamptz, reviewed_by   bigint,
    approved_at      timestamptz, approved_by   bigint,
    cancelled_at     timestamptz, cancelled_by  bigint,

    version          bigint        not null default 0,
    created_at       timestamptz   not null default now(),
    created_by       bigint,
    updated_at       timestamptz   not null default now(),
    updated_by       bigint
);
-- at most one live batch per month
create unique index uq_salary_batch_active_month
    on driver_salary_batches(period_month) where (status <> 'CANCELLED');

create table driver_salary_items (
    id                bigint        generated always as identity primary key,
    batch_id          bigint        not null references driver_salary_batches(id),
    driver_id         bigint        not null references drivers(id),
    vehicle_id        bigint        references vehicles(id),
    salary_type       text          not null,

    -- earnings
    basic_salary      numeric(12,2) not null default 0,
    working_days      numeric(5,1)  not null default 30,
    allowances        numeric(12,2) not null default 0,
    overtime          numeric(12,2) not null default 0,
    other_earnings    numeric(12,2) not null default 0,
    -- deductions
    salary_advance    numeric(12,2) not null default 0,
    loan_recovery     numeric(12,2) not null default 0,
    penalty           numeric(12,2) not null default 0,
    other_deduction   numeric(12,2) not null default 0,
    -- totals
    gross_amount      numeric(12,2) not null default 0,
    total_deduction   numeric(12,2) not null default 0,
    net_salary        numeric(12,2) not null default 0,

    payment_status    text          not null default 'PENDING'
                      check (payment_status in ('PENDING','PROCESSING','PAID','FAILED','ON_HOLD')),
    payment_method    text          check (payment_method in ('BANK_TRANSFER','CASH','OTHER')),
    payment_reference text,
    paid_at           timestamptz,
    paid_by           bigint,
    failure_reason    text,
    note              text,

    version           bigint        not null default 0,
    created_at        timestamptz   not null default now(),
    created_by        bigint,
    updated_at        timestamptz   not null default now(),
    updated_by        bigint,
    constraint uq_salary_item_driver unique (batch_id, driver_id)
);
create index ix_salary_items_batch on driver_salary_items(batch_id);
