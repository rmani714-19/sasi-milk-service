-- =====================================================================
-- V23  Multiple business bank accounts on the centre (was a single
--      payout account in V21). Each account is added, then VERIFIED by an
--      authorized user before it can be used. When processing a farmer
--      settlement or a driver-salary batch the operator picks which
--      verified account to pay from, records that account's balance, and
--      the system checks it covers the batch before approval.
-- =====================================================================

create table center_bank_accounts (
    id                    bigint      generated always as identity primary key,
    public_id             uuid        not null default gen_random_uuid() unique,

    account_holder_name   text        not null,
    account_number_enc    text        not null,   -- AES-GCM, never returned to clients
    account_number_last4  text        not null,
    account_number_hash   text        not null,   -- blind index, for change detection
    ifsc                  text        not null,
    bank_name             text        not null,
    label                 text,                   -- optional friendly name ("HDFC Current – main")

    status                text        not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    verified              boolean     not null default false,
    verified_at           timestamptz,
    verified_by           bigint,
    is_primary            boolean     not null default false,

    version               bigint      not null default 0,
    created_at            timestamptz not null default now(),
    created_by            bigint,
    updated_at            timestamptz not null default now(),
    updated_by            bigint
);

-- at most one primary among the active accounts
create unique index uq_center_bank_account_primary
    on center_bank_accounts (is_primary) where is_primary and status = 'ACTIVE';

-- carry the V21 single payout account (if one was configured) in as the first,
-- already-verified, primary account
insert into center_bank_accounts
    (account_holder_name, account_number_enc, account_number_last4, account_number_hash,
     ifsc, bank_name, label, status, verified, verified_at, verified_by, is_primary, created_by)
select
    payout_account_holder_name, payout_account_number_enc, payout_account_number_last4,
    payout_account_number_hash, payout_ifsc, payout_bank_name, 'Primary business account',
    'ACTIVE', true, coalesce(payout_account_updated_at, now()), payout_account_updated_by,
    true, payout_account_updated_by
from center
where payout_account_holder_name is not null
  and payout_account_number_enc is not null
  and payout_ifsc is not null
  and payout_bank_name is not null;

alter table center
    drop column payout_account_holder_name,
    drop column payout_account_number_enc,
    drop column payout_account_number_last4,
    drop column payout_account_number_hash,
    drop column payout_ifsc,
    drop column payout_bank_name,
    drop column payout_account_updated_at,
    drop column payout_account_updated_by;

-- which account a balance check was recorded against
alter table payment_bank_balance_checks
    add column center_bank_account_id bigint;
