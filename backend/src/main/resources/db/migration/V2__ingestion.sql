-- Phase 2: upload jobs, runs, test cases, results

-- A queue of uploaded reports, consumed by the worker with FOR UPDATE SKIP LOCKED (ADR-0002).
create table ingestion_jobs (
    id              uuid primary key,
    project_id      uuid        not null references projects (id) on delete cascade,
    api_key_id      uuid        references api_keys (id) on delete set null,
    idempotency_key text        not null,
    format          text        not null check (format in ('JUNIT')),
    status          text        not null check (status in ('QUEUED', 'PROCESSING', 'DONE', 'FAILED')),
    attempts        int         not null default 0,
    locked_at       timestamptz,
    error           text,
    run_id          bigint,
    file_count      int         not null,
    -- run metadata sent by the uploader
    commit_sha      text,
    branch          text,
    ci_provider     text,
    build_url       text,
    triggered_at    timestamptz not null,
    created_at      timestamptz not null default now(),
    finished_at     timestamptz,
    unique (project_id, idempotency_key)
);
create index ingestion_jobs_queued_idx on ingestion_jobs (created_at) where status = 'QUEUED';
create index ingestion_jobs_processing_idx on ingestion_jobs (locked_at) where status = 'PROCESSING';
create index ingestion_jobs_project_idx on ingestion_jobs (project_id, created_at desc);

-- The uploaded files (gzip-compressed), deleted as soon as the job is processed.
create table ingestion_job_files (
    job_id    uuid  not null references ingestion_jobs (id) on delete cascade,
    seq       int   not null,
    file_name text  not null,
    content   bytea not null,
    primary key (job_id, seq)
);

create table test_runs (
    id           bigint generated always as identity primary key,
    project_id   uuid        not null references projects (id) on delete cascade,
    commit_sha   text,
    branch       text,
    ci_provider  text,
    build_url    text,
    triggered_at timestamptz not null,
    duration_ms  bigint      not null,
    total        int         not null,
    passed       int         not null,
    failed       int         not null,
    skipped      int         not null,
    flaky        int         not null,
    created_at   timestamptz not null default now()
);
create index test_runs_project_time_idx on test_runs (project_id, triggered_at desc);

-- fingerprint = SHA-256 of "suite::name", so moving a file does not reset a test's history
create table test_cases (
    id          bigint generated always as identity primary key,
    project_id  uuid        not null references projects (id) on delete cascade,
    fingerprint text        not null,
    suite       text        not null,
    name        text        not null,
    file        text,
    first_seen  timestamptz not null,
    last_seen   timestamptz not null,
    unique (project_id, fingerprint)
);

create table test_results (
    id              bigint generated always as identity primary key,
    run_id          bigint   not null references test_runs (id) on delete cascade,
    test_case_id    bigint   not null references test_cases (id) on delete cascade,
    outcome         text     not null check (outcome in ('PASSED', 'FAILED', 'SKIPPED', 'PASSED_ON_RETRY')),
    attempts        smallint not null,
    failed_attempts smallint not null,
    duration_ms     bigint   not null,
    failure_type    text,
    message         text,     -- truncated to 2 KB
    stack           text,     -- truncated to 8 KB
    unique (run_id, test_case_id)
);
create index test_results_case_run_idx on test_results (test_case_id, run_id desc);
