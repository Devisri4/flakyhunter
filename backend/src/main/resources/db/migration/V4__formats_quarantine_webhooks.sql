-- Phase 5: Playwright reports (and automatic detection)
alter table ingestion_jobs drop constraint ingestion_jobs_format_check;
alter table ingestion_jobs add constraint ingestion_jobs_format_check
    check (format in ('AUTO', 'JUNIT', 'PLAYWRIGHT'));

-- Phase 6: quarantine — a member takes a test out of CI for a while, with a reason
create table quarantines (
    id           bigint generated always as identity primary key,
    test_case_id bigint      not null references test_cases (id) on delete cascade,
    reason       text        not null,
    created_by   uuid        references users (id) on delete set null,
    created_at   timestamptz not null default now(),
    expires_at   timestamptz not null,
    lifted_at    timestamptz,
    lifted_by    uuid        references users (id) on delete set null,
    lift_reason  text        -- 'manual' or 'expired'
);
-- at most one active quarantine per test
create unique index quarantines_active_uq on quarantines (test_case_id) where lifted_at is null;
create index quarantines_expiry_idx on quarantines (expires_at) where lifted_at is null;

-- Phase 6: alerts when a test becomes flaky
create table webhooks (
    id               uuid primary key,
    project_id       uuid        not null references projects (id) on delete cascade,
    kind             text        not null check (kind in ('SLACK', 'DISCORD', 'GENERIC')),
    url              text        not null,
    secret           text        not null, -- HMAC key for the generic signature header
    enabled          boolean     not null default true,
    created_at       timestamptz not null default now(),
    last_status      text,
    last_delivery_at timestamptz
);
create index webhooks_project_idx on webhooks (project_id);
