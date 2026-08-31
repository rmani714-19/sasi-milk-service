-- =====================================================================
-- V7  Notification templates, channel config, notification log
-- =====================================================================

create table notification_templates (
    id                   bigint      generated always as identity primary key,
    code                 text        not null,   -- PAYMENT_SUCCESS, PAYMENT_FAILED, COLLECTION_CREATED, ...
    channel              text        not null check (channel in ('SMS','WHATSAPP','EMAIL')),
    locale               text        not null default 'en',
    subject              text,                   -- EMAIL only
    body                 text        not null,   -- {{mustache}} placeholders
    provider_template_id text,                   -- DLT template id / WhatsApp template name
    status               text        not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    version              bigint      not null default 0,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    updated_by           bigint,
    constraint ux_notification_template unique (code, channel, locale)
);

create table notification_channel_config (
    id         bigint      generated always as identity primary key,
    channel    text        not null unique check (channel in ('SMS','WHATSAPP','EMAIL')),
    enabled    boolean     not null default false,
    provider   text,
    sender_id  text,
    config     jsonb       not null default '{}'::jsonb,   -- non-secret config only; secrets in AWS Secrets Manager
    updated_at timestamptz not null default now(),
    updated_by bigint
);

create table notifications (
    id                  bigint      generated always as identity primary key,
    event_type          text        not null,
    channel             text        not null check (channel in ('SMS','WHATSAPP','EMAIL')),
    recipient           text        not null,          -- phone / email
    producer_id         bigint      references producers(id),
    template_code       text,
    entity_type         text,
    entity_id           bigint,
    dedupe_key          text        not null unique,   -- event_type + entity + channel + recipient
    payload             jsonb       not null default '{}'::jsonb,
    rendered_body       text,
    status              text        not null default 'QUEUED'
                        check (status in ('QUEUED','SENDING','SENT','DELIVERED','FAILED','SKIPPED')),
    attempt_count       int         not null default 0,
    provider            text,
    provider_message_id text,
    provider_status     text,
    error_detail        text,
    queued_at           timestamptz not null default now(),
    sent_at             timestamptz,
    delivered_at        timestamptz,
    updated_at          timestamptz not null default now()
);
create index ix_notifications_status   on notifications(status);
create index ix_notifications_producer on notifications(producer_id);
create index ix_notifications_entity   on notifications(entity_type, entity_id);
