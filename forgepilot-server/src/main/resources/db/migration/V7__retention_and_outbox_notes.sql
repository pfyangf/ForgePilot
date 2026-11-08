-- 平台默认工作区保留宽限（天）。应用配置 forgepilot.workspace.retention-grace-days 可覆盖。
insert into retention_policy (id, scope, tenant_id, workspace_retention_days, artifact_retention_days)
select
    '70000000-0000-4000-8000-000000000001',
    'PLATFORM',
    null,
    7,
    30
where not exists (
    select 1 from retention_policy where scope = 'PLATFORM' and tenant_id is null
);
