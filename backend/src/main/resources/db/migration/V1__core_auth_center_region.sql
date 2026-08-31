-- =====================================================================
-- V1  Core: extensions, roles/users/auth, center config, regions, shifts
-- =====================================================================

create extension if not exists pgcrypto;    -- gen_random_uuid()
create extension if not exists btree_gist;  -- GiST EXCLUDE constraints on ranges

-- ---------------------------------------------------------------------
-- roles / users / user_roles
-- ---------------------------------------------------------------------
create table roles (
    id          bigint generated always as identity primary key,
    code        text        not null unique,
    name        text        not null,
    description text,
    permissions jsonb       not null default '[]'::jsonb,   -- array of permission codes
    is_system   boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table users (
    id                    bigint generated always as identity primary key,
    public_id             uuid        not null default gen_random_uuid() unique,
    username              text        not null unique,
    email                 text        unique,
    mobile_number         text,
    full_name             text        not null,
    password_hash         text        not null,             -- bcrypt/argon2 (Spring Security)
    status                text        not null default 'ACTIVE'
                          check (status in ('ACTIVE','INACTIVE','LOCKED')),
    failed_login_attempts int         not null default 0,
    last_login_at         timestamptz,
    password_changed_at   timestamptz not null default now(),
    must_change_password  boolean     not null default false,
    version               bigint      not null default 0,
    created_at            timestamptz not null default now(),
    created_by            bigint,
    updated_at            timestamptz not null default now(),
    updated_by            bigint
);

create table user_roles (
    user_id     bigint not null references users(id),
    role_id     bigint not null references roles(id),
    assigned_at timestamptz not null default now(),
    assigned_by bigint,
    primary key (user_id, role_id)
);

-- refresh-token rotation: store hash only, chain via replaced_by
create table refresh_tokens (
    id          bigint generated always as identity primary key,
    user_id     bigint      not null references users(id),
    token_hash  text        not null unique,
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by bigint      references refresh_tokens(id),
    user_agent  text,
    ip_address  text
);
create index ix_refresh_tokens_user on refresh_tokens(user_id) where revoked_at is null;

-- ---------------------------------------------------------------------
-- center: single logical row, holds configurable business rules
-- ---------------------------------------------------------------------
create table center (
    id                     bigint generated always as identity primary key,
    center_name            text        not null,
    owner_name             text,
    mobile_number          text,
    email                  text,
    address                text,
    logo_object_key        text,                              -- S3 key
    currency_code          text        not null default 'INR',
    timezone               text        not null default 'Asia/Kolkata',

    settlement_day_of_week int         not null default 1     -- ISO-8601: 1=Mon .. 7=Sun
                           check (settlement_day_of_week between 1 and 7),

    -- "round as per banking" (client): nearest whole rupee, banker's rounding.
    rounding_mode          text        not null default 'HALF_EVEN'
                           check (rounding_mode in
                                  ('HALF_UP','HALF_EVEN','HALF_DOWN','UP','DOWN','CEILING','FLOOR','NONE')),
    rounding_scale         int         not null default 0,    -- 0 = round final payable to whole rupee

    -- incentive: common + individual combined per incentive_stacking, then capped.
    -- client: "Max rs.2 ... by Admin" -> total incentive per litre never exceeds this.
    incentive_stacking     text         not null default 'ADD'
                           check (incentive_stacking in ('ADD','REPLACE')),
    incentive_max_per_litre numeric(10,4) not null default 2.0000 check (incentive_max_per_litre >= 0),

    -- rate is a formula, not a matrix (client, OPEN_QUESTIONS #1/#3):
    --   rate_per_litre = (fat_percentage + snf_percentage) * ts_value / 100
    -- ts_value lives in rate_charts (COMMON, with optional REGION override), effective-dated.
    allow_missing_rate     boolean     not null default false,

    fat_min                numeric(4,2) not null default 2.00,
    fat_max                numeric(4,2) not null default 14.00,
    snf_min                numeric(4,2) not null default 6.00,
    snf_max                numeric(4,2) not null default 12.00,

    version                bigint      not null default 0,
    created_at             timestamptz not null default now(),
    created_by             bigint,
    updated_at             timestamptz not null default now(),
    updated_by             bigint,
    constraint ck_center_fat_range check (fat_max > fat_min),
    constraint ck_center_snf_range check (snf_max > snf_min)
);

-- ---------------------------------------------------------------------
-- regions
-- ---------------------------------------------------------------------
create table regions (
    id          bigint generated always as identity primary key,
    code        text        not null unique,      -- REGION_1 ..
    name        text        not null,
    description text,
    status      text        not null default 'ACTIVE'
                check (status in ('ACTIVE','INACTIVE')),
    version     bigint      not null default 0,
    created_at  timestamptz not null default now(),
    created_by  bigint,
    updated_at  timestamptz not null default now(),
    updated_by  bigint
);

-- ---------------------------------------------------------------------
-- shifts (reference table; milk_collections.shift FKs to this)
-- ---------------------------------------------------------------------
create table shifts (
    code       text primary key,        -- MORNING, EVENING
    name       text not null,
    sort_order int  not null,
    start_time time,
    end_time   time,
    status     text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE'))
);
