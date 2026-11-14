-- V28  Payments are manual: the owner pays through the bank and records the outcome in the app.
-- The payment-gateway integration is gone, so its recipient-mapping and webhook-event tables
-- (both empty — see V20) are dropped. payouts.provider stays and always reads 'MANUAL'.
drop table if exists payment_webhook_events;
drop table if exists payment_provider_accounts;
