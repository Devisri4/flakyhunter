-- Phase 3: flakiness stats, state history, failure signatures, daily aggregates

-- Stats kept up to date incrementally, so the dashboard never scans history (ADR-0004/0005).
-- window_outcomes: one char per recent executed run, oldest first: P pass, F fail, R passed after retry.
alter table test_cases
    add column window_outcomes  varchar(30)  not null default '',
    add column window_commits   text[]       not null default '{}',
    add column runs_in_window   int          not null default 0,
    add column flip_count       int          not null default 0,
    add column retry_pass_count int          not null default 0,
    add column fail_streak      int          not null default 0,
    add column score            numeric(4,3) not null default 0,
    add column state            text         not null default 'NEW'
        check (state in ('NEW', 'STABLE', 'SUSPECT', 'FLAKY', 'BROKEN')),
    add column stats_updated_at timestamptz;
create index test_cases_state_score_idx on test_cases (project_id, state, score desc);

create table failure_signatures (
    id                 bigint generated always as identity primary key,
    project_id         uuid        not null references projects (id) on delete cascade,
    hash               text        not null,
    exception_type     text        not null,
    normalized_message text        not null,
    sample_stack       text,
    first_seen         timestamptz not null,
    last_seen          timestamptz not null,
    occurrences        int         not null default 0,
    unique (project_id, hash)
);
create index failure_signatures_project_idx on failure_signatures (project_id, occurrences desc);

alter table test_results add column signature_id bigint references failure_signatures (id) on delete set null;
create index test_results_signature_idx on test_results (signature_id) where signature_id is not null;

create table state_changes (
    id           bigint generated always as identity primary key,
    test_case_id bigint       not null references test_cases (id) on delete cascade,
    from_state   text,
    to_state     text         not null,
    score        numeric(4,3) not null,
    run_id       bigint       references test_runs (id) on delete set null,
    changed_at   timestamptz  not null default now()
);
create index state_changes_case_idx on state_changes (test_case_id, changed_at desc);
create index state_changes_time_idx on state_changes (changed_at desc);

-- Cheap trend charts: one row per project and day (kept forever).
create table daily_stats (
    project_id        uuid   not null references projects (id) on delete cascade,
    day               date   not null,
    runs              int    not null default 0,
    tests_executed    bigint not null default 0,
    passed            bigint not null default 0,
    failed            bigint not null default 0,
    flaky             bigint not null default 0,
    total_duration_ms bigint not null default 0,
    primary key (project_id, day)
);

-- When each maintenance job last ran (the free server sleeps, so "nightly" means "when due").
create table maintenance_log (
    name        text primary key,
    last_run_at timestamptz not null
);
