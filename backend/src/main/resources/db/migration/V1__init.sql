-- Phase 1: users, sessions, projects, members, API keys

create table users (
    id            uuid primary key,
    email         text        not null,  -- stored lower-case
    password_hash text        not null,
    display_name  text        not null,
    created_at    timestamptz not null default now()
);
create unique index users_email_uq on users (lower(email));

-- Refresh tokens are stored hashed. Each login starts a "family"; reusing a rotated
-- token revokes the whole family (token theft detection).
create table refresh_tokens (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    family_id   uuid        not null,
    token_hash  text        not null unique,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by uuid,
    created_at  timestamptz not null default now()
);
create index refresh_tokens_family_idx on refresh_tokens (family_id);
create index refresh_tokens_user_idx on refresh_tokens (user_id);

create table projects (
    id             uuid primary key,
    slug           text        not null unique,
    name           text        not null,
    default_branch text        not null default 'main',
    is_public_demo boolean     not null default false,
    created_at     timestamptz not null default now()
);

create table project_members (
    project_id uuid        not null references projects (id) on delete cascade,
    user_id    uuid        not null references users (id) on delete cascade,
    role       text        not null check (role in ('OWNER', 'MEMBER', 'VIEWER')),
    created_at timestamptz not null default now(),
    primary key (project_id, user_id)
);
create index project_members_user_idx on project_members (user_id);

-- Only the SHA-256 hash of a key is stored; the full key is shown once at creation.
create table api_keys (
    id           uuid primary key,
    project_id   uuid        not null references projects (id) on delete cascade,
    name         text        not null,
    prefix       text        not null,
    key_hash     text        not null unique,
    created_by   uuid        references users (id) on delete set null,
    created_at   timestamptz not null default now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);
create index api_keys_project_idx on api_keys (project_id);
