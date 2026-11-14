-- =====================================================================
-- V27  Payment preparation: bills, bank submission record, status history
--
--      Payments are still processed manually through the owner's bank.
--      Nothing here moves money — it records the paperwork around it:
--        * a bill number on each producer's settlement line (set when
--          bills are generated from verified collections),
--        * the date/reference the batch was handed to the bank,
--        * a complete, append-only history of every status change.
-- =====================================================================

alter table settlement_items
    add column bill_number       text,
    add column bill_generated_at timestamptz,
    add column bill_generated_by bigint;

create unique index uq_settlement_item_bill_number
    on settlement_items(bill_number) where bill_number is not null;

-- One submission record per settlement run: when it went to the bank.
create table settlement_bank_submissions (
    id             bigint      generated always as identity primary key,
    settlement_id  bigint      not null unique references settlements(id),
    submitted_on   date        not null,
    bank_reference text,
    note           text,
    producer_count integer     not null default 0,
    total_amount   numeric(16, 2) not null default 0,
    submitted_by   bigint,
    created_at     timestamptz not null default now()
);

-- Append-only status trail for a run and its producer lines.
create table settlement_status_history (
    id            bigint      generated always as identity primary key,
    settlement_id bigint      not null references settlements(id),
    subject_type  text        not null check (subject_type in ('SETTLEMENT', 'ITEM')),
    subject_id    bigint      not null,
    from_status   text,
    to_status     text        not null,
    note          text,
    changed_by    bigint,
    changed_at    timestamptz not null default now()
);
create index ix_settlement_status_history on settlement_status_history(settlement_id, id);
