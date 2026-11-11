-- 任务冻结仓库补充工作分支；会话快照扩展为文档包；发布记录合并目标分支。

alter table task_repository
    add column if not exists working_branch varchar(256);

update task_repository tr
set working_branch = t.branch_name
from task t
where tr.task_id = t.id
  and tr.working_branch is null
  and t.branch_name is not null
  and t.branch_name <> '';

alter table session_convention_snapshot
    add column if not exists kind varchar(32) not null default 'CONVENTION',
    add column if not exists source_ref varchar(256),
    add column if not exists body text,
    add column if not exists included boolean not null default true,
    add column if not exists sort_order integer not null default 0;

create table if not exists requirement_release_target (
    id uuid primary key,
    release_id uuid not null references requirement_release (id),
    project_repository_id uuid not null references project_repository (id),
    source_branch varchar(256) not null,
    target_branch varchar(256) not null,
    merge_sha varchar(64),
    status varchar(32) not null,
    message text,
    created_at timestamptz not null default now(),
    unique (release_id, project_repository_id, source_branch),
    constraint requirement_release_target_status_ck check (status in ('MERGED', 'ALREADY_MERGED'))
);

create index if not exists idx_requirement_release_target_release
    on requirement_release_target (release_id);
