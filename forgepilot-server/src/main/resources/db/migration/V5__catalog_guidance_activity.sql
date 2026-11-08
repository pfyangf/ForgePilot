-- 编码工具目录按租户隔离；规约合成快照可在尚无 Agent Session 时落库。
-- 演示 Provider 不含真实生产密钥，密文仅为本地占位。

alter table agent_profile
    add column if not exists tenant_id uuid references tenant (id);

create index if not exists idx_agent_profile_tenant on agent_profile (tenant_id);

create table convention_composition (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    project_id uuid not null references project (id),
    composition_hash varchar(128) not null,
    created_by uuid,
    created_at timestamptz not null default now()
);

create index idx_convention_composition_project on convention_composition (project_id, created_at desc);

create table convention_composition_item (
    id uuid primary key,
    composition_id uuid not null references convention_composition (id),
    guidance_item_id uuid references guidance_item (id),
    scope varchar(16) not null,
    title varchar(256) not null,
    body text,
    content_hash varchar(128) not null,
    created_at timestamptz not null default now()
);

create index idx_convention_composition_item on convention_composition_item (composition_id);

-- 演示科技：两条模型目录（一条 Responses，一条需网关的 Chat Completions）+ Codex 工具
insert into integration_secret (id, owner_type, owner_id, purpose, ciphertext, key_version) values
    ('70000000-0000-4000-8000-000000000001', 'MODEL_PROVIDER', '71000000-0000-4000-8000-000000000001',
     'MODEL_PROVIDER', convert_to('PLACEHOLDER_NOT_A_REAL_KEY', 'UTF8'), 1),
    ('70000000-0000-4000-8000-000000000002', 'MODEL_PROVIDER', '71000000-0000-4000-8000-000000000002',
     'MODEL_PROVIDER', convert_to('PLACEHOLDER_NEEDS_GATEWAY', 'UTF8'), 1);

insert into model_provider (
    id, tenant_id, name, provider_type, base_url, secret_id, enabled, api_version,
    upstream_wire_api, translation_gateway, created_by
) values
    ('71000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
     '本地 OpenAI 兼容（Responses）', 'OPENAI_COMPAT', 'http://127.0.0.1:18080/v1',
     '70000000-0000-4000-8000-000000000001', true, null, 'responses', null,
     '20000000-0000-4000-8000-000000000002'),
    ('71000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
     '本地 Chat Completions（需网关）', 'OPENAI_COMPAT', 'http://127.0.0.1:18081/v1',
     '70000000-0000-4000-8000-000000000002', true, null, 'chat_completions', null,
     '20000000-0000-4000-8000-000000000002');

-- 回填 secret.owner_id 已指向 provider；上面插入顺序先 secret 后 provider，owner_id 预先写成 provider id。

insert into agent_profile (
    id, tenant_id, name, agent_type, version, launch_command, model_provider_id, model_id,
    interactive_input, native_pause, interrupt, event_stream, tool_events, session_resume,
    structured_result, timeout_seconds, enabled, created_by
) values
    ('72000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
     'Codex CLI', 'CODEX_CLI', '0.1', 'codex', '71000000-0000-4000-8000-000000000001', 'gpt-5',
     false, false, true, true, true, true, true, 3600, true,
     '20000000-0000-4000-8000-000000000002');

update project
set default_agent_profile_id = '72000000-0000-4000-8000-000000000001',
    default_model_provider_id = '71000000-0000-4000-8000-000000000001',
    default_model_id = 'gpt-5'
where id = '30000000-0000-4000-8000-000000000001';

insert into guidance_item (
    id, scope, tenant_id, project_id, kind, title, category, body, enabled, include_in_agent, content_hash, created_by
) values
    ('73000000-0000-4000-8000-000000000001', 'PLATFORM', null, null, 'CONVENTION', '提交说明',
     'git', '平台要求写明动机', true, true, 'platform-commit', null),
    ('73000000-0000-4000-8000-000000000002', 'TENANT', '10000000-0000-4000-8000-000000000001', null,
     'CONVENTION', '提交说明', 'git', '公司要求带工单号', true, true, 'tenant-commit',
     '20000000-0000-4000-8000-000000000002'),
    ('73000000-0000-4000-8000-000000000003', 'PROJECT', '10000000-0000-4000-8000-000000000001',
     '30000000-0000-4000-8000-000000000001', 'CONVENTION', '模块边界', 'architecture',
     'web 不直接访问支付库', true, true, 'project-boundary',
     '20000000-0000-4000-8000-000000000002'),
    ('73000000-0000-4000-8000-000000000004', 'PROJECT', '10000000-0000-4000-8000-000000000001',
     '30000000-0000-4000-8000-000000000001', 'DOCUMENT', '架构笔记', 'docs',
     '给人看的长文，默认不进 Agent', true, false, 'project-note',
     '20000000-0000-4000-8000-000000000002');
