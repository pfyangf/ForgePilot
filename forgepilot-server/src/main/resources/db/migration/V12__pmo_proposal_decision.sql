-- 平台 PMO 下一步建议的确认与退回。同一指纹退回后不再出现，直到状态、任务或缺陷变化导致指纹改变。

create table if not exists pmo_proposal_decision (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    project_id uuid not null references project (id),
    requirement_id uuid not null references requirement (id),
    proposal_id varchar(64) not null,
    fingerprint text not null,
    kind varchar(32) not null,
    task_type varchar(32),
    defect_id uuid references defect (id),
    decision varchar(16) not null,
    comment text,
    created_task_id uuid references task (id),
    actor_user_id uuid not null references sys_user (id),
    created_at timestamptz not null default now(),
    unique (requirement_id, proposal_id),
    constraint pmo_proposal_decision_decision_ck check (decision in ('CONFIRMED', 'RETURNED')),
    constraint pmo_proposal_decision_kind_ck check (kind in (
        'CREATE_TASK',
        'BIND_PROJECT',
        'REVIEW_CODE',
        'VERIFY_REGRESSION',
        'CONFIRM_ANALYSIS',
        'REVIEW_REQUIREMENT',
        'SUBMIT_DESIGN',
        'REVIEW_DESIGN',
        'ACCEPT',
        'RELEASE',
        'VIEW_RELEASE'
    ))
);

create index if not exists idx_pmo_proposal_decision_requirement
    on pmo_proposal_decision (requirement_id, created_at desc);

create index if not exists idx_pmo_proposal_decision_project
    on pmo_proposal_decision (project_id);
