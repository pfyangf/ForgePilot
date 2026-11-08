-- ForgePilot V1.2 核心表。
-- 一个项目多条 project_repository。一个任务一个工作区目录，其内每个仓库一次 clone。
-- 模型长期密钥只进 integration_secret.ciphertext，不进前端，也不进 Agent 容器。
-- 并发任务不共享可写检出、Agent 家目录、容器或容器网络。bare mirror 按节点只读共享。
-- 租户是公司级隔离边界。普通用户只属于一个租户。平台管理员不挂租户。
-- 任务启动时把仓库集合和各仓库基线分支冻结到 task_repository。不设仓库排他锁。

create table tenant (
    id uuid primary key,
    name varchar(128) not null,
    code varchar(64) not null unique,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tenant_status_ck check (status in ('ACTIVE', 'ARCHIVED'))
);

create table retention_policy (
    id uuid primary key,
    scope varchar(16) not null,
    tenant_id uuid references tenant (id),
    workspace_retention_days integer,
    artifact_retention_days integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint retention_scope_ck check (scope in ('PLATFORM', 'TENANT')),
    constraint retention_owner_ck check (
        (scope = 'PLATFORM' and tenant_id is null)
        or (scope = 'TENANT' and tenant_id is not null)
    )
);

create unique index uk_retention_platform on retention_policy (scope) where tenant_id is null;
create unique index uk_retention_tenant on retention_policy (tenant_id) where tenant_id is not null;

create table sys_user (
    id uuid primary key,
    tenant_id uuid references tenant (id),
    username varchar(64) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(128) not null,
    enabled boolean not null default true,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now()
);

create table sys_role (
    id uuid primary key,
    code varchar(64) not null unique,
    name varchar(64) not null
);

create table sys_menu_permission (
    id uuid primary key,
    role_id uuid not null references sys_role (id),
    menu_code varchar(64) not null,
    name varchar(128) not null,
    unique (role_id, menu_code)
);

create table sys_user_role (
    user_id uuid not null references sys_user (id),
    role_id uuid not null references sys_role (id),
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create table integration_secret (
    id uuid primary key,
    owner_type varchar(64) not null,
    owner_id uuid not null,
    purpose varchar(64) not null,
    ciphertext bytea not null,
    key_version integer not null default 1,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint integration_secret_owner_ck check (owner_type in ('PROJECT_REPOSITORY', 'MODEL_PROVIDER')),
    constraint integration_secret_purpose_ck check (purpose in ('GIT_HOST', 'MODEL_PROVIDER'))
);

create table model_provider (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    name varchar(128) not null,
    provider_type varchar(64) not null,
    base_url varchar(512) not null,
    secret_id uuid references integration_secret (id),
    enabled boolean not null default false,
    api_version varchar(64),
    upstream_wire_api varchar(32) not null,
    translation_gateway varchar(64),
    extra_config jsonb not null default '{}'::jsonb,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint model_provider_wire_ck check (upstream_wire_api in ('responses', 'chat_completions', 'native'))
);

create table agent_profile (
    id uuid primary key,
    name varchar(128) not null,
    agent_type varchar(64) not null,
    version varchar(64),
    launch_command varchar(512),
    model_provider_id uuid references model_provider (id),
    model_id varchar(128),
    interactive_input boolean not null default false,
    native_pause boolean not null default false,
    interrupt boolean not null default false,
    event_stream boolean not null default false,
    tool_events boolean not null default false,
    session_resume boolean not null default false,
    structured_result boolean not null default false,
    timeout_seconds integer,
    enabled boolean not null default false,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now()
);

create table project (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    name varchar(128) not null,
    code varchar(64) not null unique,
    description text,
    tech_stack varchar(128),
    status varchar(32) not null default 'ACTIVE',
    default_agent_profile_id uuid references agent_profile (id),
    default_model_provider_id uuid references model_provider (id),
    default_model_id varchar(128),
    feedback_limit integer not null default 2,
    build_command text,
    test_command text,
    verification_timeout_seconds integer,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint project_status_ck check (status in ('ACTIVE', 'ARCHIVED'))
);

create table project_member (
    id uuid primary key,
    project_id uuid not null references project (id),
    user_id uuid not null references sys_user (id),
    project_admin boolean not null default false,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    unique (project_id, user_id)
);

create table project_repository (
    id uuid primary key,
    project_id uuid not null references project (id),
    provider_type varchar(32) not null,
    display_name varchar(128) not null,
    clone_url varchar(512) not null,
    local_directory_name varchar(128) not null,
    default_branch varchar(256),
    external_id varchar(256),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    unique (project_id, local_directory_name),
    unique (project_id, clone_url),
    constraint project_repository_dir_ck check (
        local_directory_name ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
    )
);

create index idx_project_repository_project on project_repository (project_id);

create table requirement (
    id uuid primary key,
    project_id uuid not null references project (id),
    code varchar(64) not null unique,
    title varchar(256) not null,
    description text,
    background text,
    scope text,
    acceptance text,
    priority varchar(16) not null default 'MEDIUM',
    owner_id uuid references sys_user (id),
    status varchar(32) not null default '待分析',
    status_locked boolean not null default false,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint requirement_status_ck check (status in (
        '待分析', '设计中', '待开发', '开发中', '测试中', '待验收', '已完成', '已取消'
    )),
    constraint requirement_priority_ck check (priority in ('HIGH', 'MEDIUM', 'LOW'))
);

create index idx_requirement_project on requirement (project_id);

create table requirement_version (
    id uuid primary key,
    requirement_id uuid not null references requirement (id),
    version_no integer not null,
    title varchar(256) not null,
    description text,
    acceptance text,
    note text,
    created_by uuid,
    created_at timestamptz not null default now(),
    unique (requirement_id, version_no)
);

create table analysis_draft (
    id uuid primary key,
    requirement_id uuid not null references requirement (id),
    requirement_version_id uuid references requirement_version (id),
    body text not null,
    confirmed_by uuid,
    confirmed_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now()
);

create table execution_node (
    id uuid primary key,
    node_key varchar(128) not null unique,
    capacity integer not null,
    labels jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'ONLINE',
    last_heartbeat_at timestamptz,
    active_sessions integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint execution_node_status_ck check (status in ('ONLINE', 'OFFLINE'))
);

create table task (
    id uuid primary key,
    project_id uuid not null references project (id),
    requirement_id uuid not null references requirement (id),
    requirement_version_id uuid references requirement_version (id),
    code varchar(64) not null unique,
    title varchar(256) not null,
    description text,
    task_type varchar(32) not null,
    priority varchar(16) not null default 'MEDIUM',
    branch_name varchar(256),
    status varchar(32) not null default 'PENDING',
    owner_id uuid references sys_user (id),
    agent_profile_id uuid references agent_profile (id),
    model_provider_id uuid references model_provider (id),
    model_id varchar(128),
    predecessor_task_id uuid references task (id),
    queued_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint task_status_ck check (status in (
        'PENDING', 'PREPARING', 'RUNNING', 'WAITING_USER', 'VERIFYING',
        'WAITING_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED'
    )),
    constraint task_priority_ck check (priority in ('HIGH', 'MEDIUM', 'LOW'))
);

create index idx_task_project on task (project_id);
create index idx_task_requirement on task (requirement_id);
create index idx_task_status on task (status);
create index idx_project_tenant on project (tenant_id);

create table task_state_history (
    id uuid primary key,
    task_id uuid not null references task (id),
    from_status varchar(32),
    to_status varchar(32) not null,
    reason text,
    session_id uuid,
    actor_id uuid,
    created_at timestamptz not null default now()
);

create table task_repository (
    id uuid primary key,
    task_id uuid not null references task (id),
    project_repository_id uuid not null references project_repository (id),
    base_branch varchar(256) not null,
    base_sha varchar(64),
    delivery_state varchar(32) not null default 'NONE',
    frozen_at timestamptz not null default now(),
    unique (task_id, project_repository_id),
    constraint task_repository_delivery_ck check (delivery_state in ('NONE', 'PUSHED', 'MR_OPEN', 'FAILED'))
);

create index idx_task_repository_repo on task_repository (project_repository_id);

create table repository_mirror (
    id uuid primary key,
    node_id uuid not null references execution_node (id),
    project_repository_id uuid not null references project_repository (id),
    mirror_path varchar(1024) not null,
    last_fetched_at timestamptz,
    created_at timestamptz not null default now(),
    unique (node_id, project_repository_id)
);

create table workspace (
    id uuid primary key,
    task_id uuid not null unique references task (id),
    node_id uuid not null references execution_node (id),
    root_path varchar(1024) not null,
    agent_home_path varchar(1024) not null,
    status varchar(32) not null,
    last_active_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    unique (node_id, root_path),
    unique (node_id, agent_home_path)
);

create table workspace_checkout (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    project_repository_id uuid not null references project_repository (id),
    repository_mirror_id uuid references repository_mirror (id),
    local_directory_name varchar(128) not null,
    clone_path varchar(1024) not null,
    base_sha varchar(64),
    branch_name varchar(256),
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    unique (workspace_id, project_repository_id),
    unique (workspace_id, local_directory_name)
);

create table workspace_snapshot (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    project_repository_id uuid not null references project_repository (id),
    sequence integer not null,
    snapshot_type varchar(32) not null,
    location varchar(1024) not null,
    sha varchar(64),
    created_at timestamptz not null default now(),
    unique (workspace_id, project_repository_id, sequence),
    constraint workspace_snapshot_type_ck check (snapshot_type in ('HIDDEN_REF', 'BUNDLE'))
);

create table agent_session (
    id uuid primary key,
    task_id uuid not null references task (id),
    agent_profile_id uuid references agent_profile (id),
    agent_type varchar(64) not null,
    agent_version varchar(64),
    model_provider_id uuid references model_provider (id),
    model_id varchar(128),
    node_id uuid references execution_node (id),
    workspace_id uuid references workspace (id),
    container_ref varchar(128),
    status varchar(32) not null,
    started_at timestamptz,
    ended_at timestamptz,
    error_summary text,
    last_event_seq bigint not null default 0,
    token_usage integer,
    convention_set_hash varchar(128),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint agent_session_status_ck check (status in (
        'CREATED', 'STARTING', 'RUNNING', 'WAITING_INPUT',
        'SUCCEEDED', 'FAILED', 'INTERRUPTED', 'CANCELLED'
    ))
);

create unique index uk_agent_session_one_active
    on agent_session (task_id)
    where status in ('CREATED', 'STARTING', 'RUNNING', 'WAITING_INPUT');

create index idx_agent_session_task on agent_session (task_id);

create table guidance_item (
    id uuid primary key,
    scope varchar(16) not null,
    tenant_id uuid references tenant (id),
    project_id uuid references project (id),
    kind varchar(16) not null,
    title varchar(256) not null,
    category varchar(64),
    body text,
    object_key varchar(1024),
    enabled boolean not null default true,
    include_in_agent boolean not null,
    content_hash varchar(128),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint guidance_scope_ck check (scope in ('PLATFORM', 'TENANT', 'PROJECT')),
    constraint guidance_kind_ck check (kind in ('CONVENTION', 'DOCUMENT')),
    constraint guidance_owner_ck check (
        (scope = 'PLATFORM' and tenant_id is null and project_id is null)
        or (scope = 'TENANT' and tenant_id is not null and project_id is null)
        or (scope = 'PROJECT' and tenant_id is not null and project_id is not null)
    )
);

create index idx_guidance_tenant on guidance_item (tenant_id);
create index idx_guidance_project on guidance_item (project_id);

create table session_convention_snapshot (
    id uuid primary key,
    session_id uuid not null references agent_session (id),
    guidance_item_id uuid references guidance_item (id),
    scope varchar(16) not null,
    title varchar(256) not null,
    content_hash varchar(128) not null,
    created_at timestamptz not null default now()
);

create index idx_session_convention_session on session_convention_snapshot (session_id);

create table agent_event (
    id uuid primary key,
    session_id uuid not null references agent_session (id),
    task_id uuid not null references task (id),
    seq bigint not null,
    event_type varchar(64) not null,
    summary text,
    created_at timestamptz not null default now(),
    unique (session_id, seq)
);

comment on table agent_event is '重要事件摘要。禁止按 token 插入。';

create table sandbox (
    id uuid primary key,
    task_id uuid not null references task (id),
    session_id uuid references agent_session (id),
    node_id uuid references execution_node (id),
    kind varchar(32) not null,
    container_ref varchar(128),
    network_name varchar(128) not null,
    image varchar(256),
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    ended_at timestamptz,
    constraint sandbox_kind_ck check (kind in ('AGENT', 'VERIFICATION'))
);

create table artifact (
    id uuid primary key,
    task_id uuid not null references task (id),
    session_id uuid references agent_session (id),
    project_repository_id uuid references project_repository (id),
    kind varchar(64) not null,
    object_key varchar(1024) not null,
    checksum varchar(128),
    byte_size bigint,
    created_at timestamptz not null default now()
);

create table evidence (
    id uuid primary key,
    task_id uuid not null references task (id),
    session_id uuid references agent_session (id),
    project_repository_id uuid references project_repository (id),
    evidence_type varchar(64) not null,
    artifact_id uuid references artifact (id),
    summary text,
    source varchar(64),
    check_status varchar(32),
    created_at timestamptz not null default now()
);

create table verification_run (
    id uuid primary key,
    task_id uuid not null references task (id),
    workspace_id uuid not null references workspace (id),
    command text not null,
    timeout_seconds integer,
    exit_code integer,
    log_artifact_id uuid references artifact (id),
    junit_artifact_id uuid references artifact (id),
    attempt integer not null default 1,
    created_at timestamptz not null default now(),
    finished_at timestamptz
);

create table approval (
    id uuid primary key,
    task_id uuid not null references task (id),
    result varchar(32) not null,
    note text,
    reviewer_id uuid references sys_user (id),
    created_at timestamptz not null default now(),
    constraint approval_result_ck check (result in ('APPROVED', 'CHANGES_REQUESTED'))
);

create table merge_request (
    id uuid primary key,
    task_id uuid not null references task (id),
    project_repository_id uuid not null references project_repository (id),
    provider_type varchar(32) not null,
    external_id varchar(128),
    url varchar(512),
    branch_name varchar(256) not null,
    head_sha varchar(64),
    idempotency_key varchar(256) not null unique,
    state varchar(32) not null,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    constraint merge_request_state_ck check (state in ('OPEN', 'CLOSED'))
);

create unique index uk_merge_request_open_task_repo
    on merge_request (task_id, project_repository_id)
    where state = 'OPEN';

create index idx_merge_request_task on merge_request (task_id);

create table delivery_record (
    id uuid primary key,
    task_id uuid not null unique references task (id),
    requirement_version_id uuid references requirement_version (id),
    summary text,
    created_at timestamptz not null default now()
);

-- 操作记录只追加。普通用户不能改、不能删。摘要和 payload 不得写入令牌、密码或 Provider 密钥。
-- tenant_id 为空只表示平台级操作。公司查询必须带自己的 tenant_id。
create table activity_log (
    id uuid primary key,
    tenant_id uuid references tenant (id),
    actor_kind varchar(16) not null,
    actor_user_id uuid references sys_user (id),
    actor_service varchar(128),
    occurred_at timestamptz not null default now(),
    action varchar(128) not null,
    target_type varchar(64) not null,
    target_id uuid,
    project_id uuid references project (id),
    requirement_id uuid references requirement (id),
    task_id uuid references task (id),
    request_id varchar(128),
    summary text not null,
    payload jsonb not null default '{}'::jsonb,
    result varchar(16) not null,
    constraint activity_actor_ck check (actor_kind in ('USER', 'SYSTEM', 'SERVICE')),
    constraint activity_result_ck check (result in ('SUCCESS', 'FAILURE')),
    constraint activity_actor_ref_ck check (
        (actor_kind = 'USER' and actor_user_id is not null)
        or (actor_kind = 'SERVICE' and actor_service is not null)
        or actor_kind = 'SYSTEM'
    )
);

create index idx_activity_tenant_time on activity_log (tenant_id, occurred_at);
create index idx_activity_project_time on activity_log (project_id, occurred_at);
create index idx_activity_actor_time on activity_log (actor_user_id, occurred_at);
create index idx_activity_action_time on activity_log (action, occurred_at);
create index idx_activity_requirement on activity_log (requirement_id);
create index idx_activity_task on activity_log (task_id);

comment on table activity_log is '可查询的操作记录，只追加。不记录工作台页面浏览。';

create table notification (
    id uuid primary key,
    user_id uuid not null references sys_user (id),
    kind varchar(64) not null,
    title varchar(256) not null,
    body text,
    task_id uuid references task (id),
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create table outbox (
    id uuid primary key,
    event_id uuid not null unique,
    event_type varchar(64) not null,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    session_id uuid,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index idx_outbox_unpublished on outbox (created_at) where published_at is null;

create table model_usage (
    id uuid primary key,
    source varchar(32) not null,
    session_id uuid references agent_session (id),
    model_provider_id uuid references model_provider (id),
    model_name varchar(128),
    input_tokens integer,
    output_tokens integer,
    created_at timestamptz not null default now(),
    constraint model_usage_source_ck check (source in ('PLATFORM', 'AGENT_PROXY'))
);
