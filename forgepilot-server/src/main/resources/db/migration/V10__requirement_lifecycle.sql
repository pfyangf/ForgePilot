-- 轻量需求研发生命周期：需求/设计评审、需求设计稿、缺陷、发布。
-- 不是工作流设计器，也不是外部缺陷系统或通用文档库。

alter table requirement drop constraint requirement_status_ck;
alter table requirement add constraint requirement_status_ck check (status in (
    '待分析', '待需求评审', '设计中', '待设计评审', '待开发',
    '开发中', '测试中', '待验收', '已完成', '已取消', '已发布'
));

alter table analysis_draft add column suggested_tasks text not null default '[]';

create table review (
    id uuid primary key,
    project_id uuid not null references project (id),
    subject_type varchar(32) not null,
    subject_id uuid not null,
    decision varchar(16) not null,
    comment text,
    reviewer_id uuid references sys_user (id),
    created_at timestamptz not null default now(),
    constraint review_subject_type_ck check (subject_type in ('REQUIREMENT', 'DESIGN_DOCUMENT')),
    constraint review_decision_ck check (decision in ('APPROVED', 'REJECTED'))
);

create index idx_review_subject on review (subject_type, subject_id, created_at desc);
create index idx_review_project on review (project_id);

create table requirement_document (
    id uuid primary key,
    requirement_id uuid not null references requirement (id),
    kind varchar(32) not null default 'DESIGN',
    title varchar(256) not null,
    body text not null default '',
    version_no integer not null,
    current_flag boolean not null default false,
    pending_review boolean not null default false,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    unique (requirement_id, kind, version_no),
    constraint requirement_document_kind_ck check (kind in ('DESIGN'))
);

create index idx_requirement_document_requirement on requirement_document (requirement_id);
create unique index uk_requirement_document_current
    on requirement_document (requirement_id, kind)
    where current_flag = true;

create table defect (
    id uuid primary key,
    project_id uuid not null references project (id),
    requirement_id uuid not null references requirement (id),
    task_id uuid references task (id),
    code varchar(64) not null unique,
    title varchar(256) not null,
    description text,
    severity varchar(16) not null default 'MEDIUM',
    status varchar(16) not null default 'OPEN',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint defect_severity_ck check (severity in ('HIGH', 'MEDIUM', 'LOW')),
    constraint defect_status_ck check (status in ('OPEN', 'IN_FIX', 'FIXED', 'VERIFIED', 'CLOSED', 'REJECTED'))
);

create index idx_defect_requirement on defect (requirement_id);
create index idx_defect_project on defect (project_id);
create index idx_defect_status on defect (status);

alter table task add column defect_id uuid references defect (id);
create index idx_task_defect on task (defect_id);

create table requirement_release (
    id uuid primary key,
    project_id uuid not null references project (id),
    version varchar(64) not null,
    notes text,
    released_by uuid,
    released_at timestamptz not null default now(),
    unique (project_id, version)
);

create index idx_requirement_release_project on requirement_release (project_id);

create table requirement_release_item (
    id uuid primary key,
    release_id uuid not null references requirement_release (id),
    requirement_id uuid not null references requirement (id),
    unique (release_id, requirement_id)
);

create index idx_requirement_release_item_requirement on requirement_release_item (requirement_id);
