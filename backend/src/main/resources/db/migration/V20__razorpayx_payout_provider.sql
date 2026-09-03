-- =====================================================================
-- V20  RazorpayX payout provider support
--
--      The payout engine (V6) already stores payouts, their transactions
--      and does signature-verified webhook dedupe. This adds two provider
--      support tables so a real RazorpayX integration can plug in behind
--      the existing PayoutProvider abstraction without touching the
--      settlement / salary calculation.
-- =====================================================================

-- Reusable provider recipient mapping: one contact + fund account per
-- (provider, bank account). The account number itself is NEVER stored here —
-- only its blind-index hash (same scheme as producer_bank_accounts).
create table payment_provider_accounts (
    id                  bigint      generated always as identity primary key,
    provider            text        not null,
    account_number_hash text        not null,
    ifsc                text        not null,
    contact_id          text        not null,
    fund_account_id     text        not null,
    created_at          timestamptz not null default now(),
    constraint uq_provider_account unique (provider, account_number_hash, ifsc)
);

-- Inbound provider webhook events, deduplicated on the provider's own event id
-- (checked before the payout is even resolved).
create table payment_webhook_events (
    id                bigint      generated always as identity primary key,
    provider          text        not null,
    event_id          text        not null,
    payout_id         bigint      references payouts(id),
    event_type        text,
    payload           jsonb,
    received_at       timestamptz not null default now(),
    processed_at      timestamptz,
    processing_status text        not null default 'RECEIVED'
                      check (processing_status in ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'FAILED')),
    error_message     text,
    constraint uq_webhook_event unique (provider, event_id)
);
create index ix_webhook_event_payout on payment_webhook_events(payout_id);
create index ix_webhook_event_status on payment_webhook_events(processing_status);
