-- =====================================================================
-- V25  Vehicle Tracking and Live Camera Management
--
--      Builds the Phase-2 vehicle stub (V8) out into a full module:
--      richer vehicle/device master data, device and driver assignment
--      history, a fast "latest location" table plus an append-only
--      location history, trips, cameras, alerts, and a provider webhook
--      event log for idempotent ingestion. GPS/camera vendor integration
--      stays a Java adapter behind VehicleTrackingProvider /
--      VehicleCameraProvider — swappable without touching this schema.
-- =====================================================================

-- ---- vehicle master: extend the V8 stub --------------------------------
alter table vehicles
    add column vehicle_type    text not null default 'OTHER'
               check (vehicle_type in ('CAR', 'VAN', 'MINI_TRUCK', 'TRUCK', 'OTHER')),
    add column make             text,
    add column model            text,
    add column model_year       smallint,
    add column driver_id        bigint references drivers(id),
    add column registration_date date,
    add column insurance_expiry  date,
    add column pollution_expiry  date,
    add column notes            text;

alter table vehicles drop constraint vehicles_status_check;
alter table vehicles add constraint vehicles_status_check
    check (status in ('ACTIVE', 'INACTIVE', 'MAINTENANCE'));

create index ix_vehicle_driver on vehicles(driver_id);

-- ---- devices: extend the V8 stub with fleet-hardware fields ------------
alter table vehicle_devices
    add column device_model      text,
    add column imei              text,
    add column serial_number     text,
    add column sim_number_masked text,
    add column firmware_version  text,
    add column installation_date date,
    add column removed_date      date,
    add column is_primary        boolean not null default true,
    add column last_seen_at      timestamptz;

alter table vehicle_devices drop constraint vehicle_devices_device_type_check;
alter table vehicle_devices add constraint vehicle_devices_device_type_check
    check (device_type in ('GPS', 'GPS_CAMERA', 'DASHCAM', 'MDVR', 'OTHER'));

alter table vehicle_devices drop constraint vehicle_devices_status_check;
alter table vehicle_devices add constraint vehicle_devices_status_check
    check (status in ('ACTIVE', 'INACTIVE', 'REMOVED', 'FAULTY'));

-- a device may move between vehicles over time; only one may be attached now
alter table vehicle_devices drop constraint ux_vehicle_device;
create unique index uq_vehicle_device_imei on vehicle_devices(imei) where imei is not null;
create unique index uq_vehicle_device_primary_active
    on vehicle_devices(vehicle_id) where is_primary and status = 'ACTIVE';

-- ---- device assignment history ------------------------------------------
create table vehicle_device_assignments (
    id            bigint      generated always as identity primary key,
    vehicle_id    bigint      not null references vehicles(id),
    device_id     bigint      not null references vehicle_devices(id),
    assigned_from date        not null,
    assigned_to   date,
    assigned_by   bigint,
    removed_by    bigint,
    remarks       text,
    created_at    timestamptz not null default now()
);
create index ix_device_assignment_vehicle on vehicle_device_assignments(vehicle_id, id desc);
create index ix_device_assignment_device on vehicle_device_assignments(device_id, id desc);
create unique index uq_device_assignment_open on vehicle_device_assignments(device_id) where assigned_to is null;

-- ---- driver assignment history -------------------------------------------
create table vehicle_driver_assignments (
    id            bigint      generated always as identity primary key,
    vehicle_id    bigint      not null references vehicles(id),
    driver_id     bigint      not null references drivers(id),
    assigned_from date        not null,
    assigned_to   date,
    assigned_by   bigint,
    remarks       text,
    created_at    timestamptz not null default now()
);
create index ix_driver_assignment_vehicle on vehicle_driver_assignments(vehicle_id, id desc);
create unique index uq_driver_assignment_open on vehicle_driver_assignments(vehicle_id) where assigned_to is null;

-- ---- fast "latest location" per vehicle (dashboard reads this) ---------
create table vehicle_latest_locations (
    id                bigint       generated always as identity primary key,
    vehicle_id        bigint       not null unique references vehicles(id),
    device_id         bigint       references vehicle_devices(id),
    latitude          numeric(9,6) not null check (latitude between -90 and 90),
    longitude         numeric(9,6) not null check (longitude between -180 and 180),
    speed_kmph        numeric(6,2),
    direction_degrees numeric(5,1),
    altitude          numeric(8,2),
    ignition_status   text check (ignition_status in ('ON', 'OFF', 'UNKNOWN')),
    motion_status     text check (motion_status in ('MOVING', 'IDLE', 'STOPPED', 'UNKNOWN')),
    gps_accuracy      numeric(8,2),
    provider_timestamp timestamptz not null,
    received_at       timestamptz  not null default now()
);

-- ---- append-only location history (evolve the V8 stub table) -----------
alter table vehicle_locations rename to vehicle_location_history;
alter table vehicle_location_history rename column recorded_at to provider_timestamp;
alter table vehicle_location_history rename column heading_deg to direction_degrees;
alter table vehicle_location_history rename column ingested_at to received_at;
alter table vehicle_location_history drop column ignition;
alter table vehicle_location_history
    add column altitude        numeric(8,2),
    add column ignition_status text check (ignition_status in ('ON', 'OFF', 'UNKNOWN')),
    add column motion_status   text check (motion_status in ('MOVING', 'IDLE', 'STOPPED', 'UNKNOWN')),
    add column gps_accuracy    numeric(8,2);
alter table vehicle_location_history alter column latitude type numeric(9,6);
alter table vehicle_location_history alter column longitude type numeric(9,6);
alter table vehicle_location_history add constraint chk_vlh_lat check (latitude between -90 and 90);
alter table vehicle_location_history add constraint chk_vlh_lng check (longitude between -180 and 180);
drop index if exists ix_vehicle_loc_vehicle_time;
create index ix_vehicle_location_history_vehicle_time
    on vehicle_location_history(vehicle_id, provider_timestamp desc);

-- ---- trips ---------------------------------------------------------------
create table vehicle_trips (
    id                 bigint      generated always as identity primary key,
    vehicle_id         bigint      not null references vehicles(id),
    device_id          bigint      references vehicle_devices(id),
    driver_id          bigint      references drivers(id),
    started_at         timestamptz not null,
    ended_at           timestamptz,
    start_latitude     numeric(9,6),
    start_longitude    numeric(9,6),
    end_latitude       numeric(9,6),
    end_longitude      numeric(9,6),
    distance_km        numeric(10,3),
    duration_seconds   bigint,
    max_speed_kmph     numeric(6,2),
    average_speed_kmph numeric(6,2),
    status             text        not null default 'ONGOING'
                       check (status in ('ONGOING', 'COMPLETED', 'UNKNOWN')),
    created_at         timestamptz not null default now()
);
create index ix_vehicle_trip_vehicle on vehicle_trips(vehicle_id, started_at desc);
create unique index uq_vehicle_trip_ongoing on vehicle_trips(vehicle_id) where status = 'ONGOING';

-- ---- cameras --------------------------------------------------------------
-- the V8 "camera_devices" stub was never wired to any code; superseded below
drop table if exists camera_devices;

create table vehicle_cameras (
    id                bigint      generated always as identity primary key,
    vehicle_id        bigint      not null references vehicles(id),
    device_id         bigint      not null references vehicle_devices(id),
    camera_type       text        not null check (camera_type in ('FRONT', 'CABIN', 'REAR', 'SIDE', 'OTHER')),
    camera_channel    smallint,
    camera_name       text,
    provider_camera_id text,
    status            text        not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE', 'FAULTY')),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index ix_vehicle_camera_vehicle on vehicle_cameras(vehicle_id);
create unique index uq_vehicle_camera_channel
    on vehicle_cameras(device_id, camera_channel) where camera_channel is not null;

-- ---- alerts ----------------------------------------------------------------
create table vehicle_alerts (
    id              bigint      generated always as identity primary key,
    vehicle_id      bigint      not null references vehicles(id),
    device_id       bigint      references vehicle_devices(id),
    driver_id       bigint      references drivers(id),
    alert_type      text        not null check (alert_type in
                    ('OVERSPEED', 'DEVICE_OFFLINE', 'IGNITION_ON', 'IGNITION_OFF', 'HARSH_BRAKING',
                     'HARSH_ACCELERATION', 'GEOFENCE_ENTRY', 'GEOFENCE_EXIT', 'CAMERA_OFFLINE',
                     'DEVICE_TAMPER', 'ACCIDENT', 'OTHER')),
    severity        text        not null check (severity in ('INFO', 'WARNING', 'CRITICAL')),
    message         text        not null,
    latitude        numeric(9,6),
    longitude       numeric(9,6),
    speed_kmph      numeric(6,2),
    occurred_at     timestamptz not null,
    acknowledged_by bigint,
    acknowledged_at timestamptz,
    status          text        not null default 'OPEN' check (status in ('OPEN', 'ACKNOWLEDGED')),
    created_at      timestamptz not null default now()
);
create index ix_vehicle_alert_vehicle on vehicle_alerts(vehicle_id, occurred_at desc);
create index ix_vehicle_alert_status on vehicle_alerts(status, occurred_at desc);
-- one open alert per (vehicle, type) — a continuing condition doesn't spam new rows
create unique index uq_vehicle_alert_open_condition
    on vehicle_alerts(vehicle_id, alert_type) where status = 'OPEN';

-- ---- provider webhook events (idempotent ingestion) -----------------------
create table vehicle_provider_webhook_events (
    id           bigint      generated always as identity primary key,
    provider     text        not null,
    event_id     text,
    event_type   text,
    payload      jsonb       not null,
    status       text        not null default 'RECEIVED'
                 check (status in ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'FAILED')),
    error_detail text,
    received_at  timestamptz not null default now()
);
create unique index uq_vehicle_webhook_event
    on vehicle_provider_webhook_events(provider, event_id) where event_id is not null;
