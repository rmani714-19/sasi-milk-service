-- V30  Region-scoped payment batches, driver-salary bank submission record, finer payment permissions.

-- 1. A weekly/monthly batch can be limited to one region/area. region_id NULL = every region (as before).
--    Batches for different regions of the same period may coexist; two batches for the SAME scope may not.
alter table settlements add column region_id bigint references regions(id);
alter table settlements drop constraint ex_settlement_no_overlap;
alter table settlements add constraint ex_settlement_no_overlap
    exclude using gist (
        payment_frequency with =,
        (coalesce(region_id, 0)) with =,
        daterange(period_start, period_end, '[]') with &&
    ) where (status <> 'CANCELLED');
create index ix_settlements_region on settlements(region_id);

-- 2. Hand-over of a driver salary batch to the bank is recorded, like farmer payment batches.
create table driver_salary_bank_submissions (
    id             bigint generated always as identity primary key,
    batch_id       bigint        not null unique references driver_salary_batches(id),
    submitted_on   date          not null,
    bank_reference text,
    note           text,
    driver_count   int           not null default 0,
    total_amount   numeric(16,2) not null default 0,
    submitted_by   bigint,
    created_at     timestamptz   not null default now()
);

-- 3. Separate rights for the new steps (existing rights keep working everywhere they did).
update roles set permissions = permissions || '["GENERATE_BILLS","SUBMIT_TO_BANK","CONFIRM_PAYMENT"]'::jsonb
 where code = 'CENTER_MANAGER' and not permissions @> '["GENERATE_BILLS"]'::jsonb;
update roles set permissions = permissions || '["GENERATE_BILLS"]'::jsonb
 where code = 'ACCOUNTS_USER' and not permissions @> '["GENERATE_BILLS"]'::jsonb;
