-- =====================================================================
-- V8  Vehicles / GPS / cameras  (Phase 2 feature — schema stub only)
-- =====================================================================

create table vehicles (
    id                  bigint      generated always as identity primary key,
    code                text        not null unique,
    registration_number text        not null unique,
    description         text,
    capacity_litres     numeric(10,2),
    status              text        not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create table vehicle_devices (
    id                 bigint      generated always as identity primary key,
    vehicle_id         bigint      not null references vehicles(id),
    device_type        text        not null check (device_type in ('GPS','CAMERA')),
    provider           text,
    external_device_id text        not null,
    config             jsonb       not null default '{}'::jsonb,
    active_from        timestamptz not null default now(),
    active_to          timestamptz,
    status             text        not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    constraint ux_vehicle_device unique (provider, external_device_id, active_from)
);
create index ix_vehicle_device_vehicle on vehicle_devices(vehicle_id);

create table vehicle_locations (
    id          bigint       generated always as identity primary key,
    vehicle_id  bigint       not null references vehicles(id),
    device_id   bigint       references vehicle_devices(id),
    recorded_at timestamptz  not null,
    latitude    numeric(9,6) not null,
    longitude   numeric(9,6) not null,
    speed_kmph  numeric(6,2),
    heading_deg numeric(6,2),
    ignition    boolean,
    raw         jsonb,
    ingested_at timestamptz  not null default now()
);
create index ix_vehicle_loc_vehicle_time on vehicle_locations(vehicle_id, recorded_at desc);

create table camera_devices (
    id                 bigint      generated always as identity primary key,
    vehicle_id         bigint      references vehicles(id),
    provider           text        not null,
    external_device_id text        not null,
    stream_url         text,
    config             jsonb       not null default '{}'::jsonb,
    status             text        not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    created_at         timestamptz not null default now(),
    constraint ux_camera_device unique (provider, external_device_id)
);
