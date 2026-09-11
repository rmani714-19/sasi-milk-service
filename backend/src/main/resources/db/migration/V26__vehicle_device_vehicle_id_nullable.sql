-- =====================================================================
-- V26  A device is no longer required to be attached to a vehicle.
--
--      V25 kept the V8 stub's "vehicle_id not null" on vehicle_devices,
--      but the new attach/detach model needs a device to exist unattached
--      (freshly registered, in stock, or just detached) before it is
--      attached to a vehicle.
-- =====================================================================

alter table vehicle_devices alter column vehicle_id drop not null;
