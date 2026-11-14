-- V29  Driver salary statements: route, advances ledger, vehicle expenses.
--
-- A driver's route is the set of regions (areas) they collect from; the salary statement reports the
-- litres collected in those regions during the month. Advances given to a driver are kept as a ledger
-- so the next salary run can deduct them. Expenses can now be tagged to a vehicle, so a driver's
-- statement can show what was spent on their vehicle.

create table driver_route_regions (
    driver_id bigint not null references drivers(id),
    region_id bigint not null references regions(id),
    primary key (driver_id, region_id)
);

create table driver_advances (
    id                  bigint generated always as identity primary key,
    driver_id           bigint        not null references drivers(id),
    advance_date        date          not null default current_date,
    amount              numeric(12,2) not null check (amount > 0),
    reason              text,
    status              text          not null default 'OPEN' check (status in ('OPEN','DEDUCTED','CANCELLED')),
    deducted_in_item_id bigint        references driver_salary_items(id),
    created_at          timestamptz   not null default now(),
    created_by          bigint
);
create index ix_driver_advances_driver on driver_advances(driver_id, status);

alter table expenses add column vehicle_id bigint references vehicles(id);
create index ix_expenses_vehicle on expenses(vehicle_id, expense_date);

alter table driver_salary_items
    add column route_litres     numeric(12,2) not null default 0,
    add column vehicle_expenses numeric(12,2) not null default 0;
