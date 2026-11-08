-- 任务进入终态时记录 terminal_at，供工作区保留宽限计算（不再用 workspace.created_at 近似）。
alter table task add column if not exists terminal_at timestamptz;

create index if not exists idx_task_terminal_at on task (terminal_at)
    where terminal_at is not null;
