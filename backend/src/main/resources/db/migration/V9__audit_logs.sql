-- =====================================================================
-- V9  Audit log (append-only)
-- =====================================================================

create table audit_logs (
    id          bigint      generated always as identity primary key,
    event_time  timestamptz not null default now(),
    user_id     bigint,
    username    text,
    action      text        not null,   -- LOGIN, LOGOUT, PRODUCER_CREATED, BANK_DETAILS_UPDATED,
                                        -- RATE_UPDATED, SETTLEMENT_APPROVED, PAYOUT_STATUS_CHANGED, ...
    entity_type text,
    entity_id   text,
    old_value   jsonb,
    new_value   jsonb,
    summary     text,
    ip_address  text,
    user_agent  text,
    request_id  text,
    created_at  timestamptz not null default now()
);
create index ix_audit_entity      on audit_logs(entity_type, entity_id);
create index ix_audit_user        on audit_logs(user_id);
create index ix_audit_action_time on audit_logs(action, event_time desc);

-- append-only guard: block UPDATE / DELETE at the database level
create or replace function audit_logs_block_mutation() returns trigger
language plpgsql as $$
begin
    raise exception 'audit_logs is append-only';
end;
$$;

create trigger trg_audit_logs_no_update
    before update or delete on audit_logs
    for each row execute function audit_logs_block_mutation();
