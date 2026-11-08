-- 本机/开发验证与交付使用的默认执行节点占位。
insert into execution_node (id, node_key, capacity, labels, status, active_sessions)
values (
    '80000000-0000-4000-8000-000000000001',
    'local-dev',
    4,
    '{"kind":"local"}'::jsonb,
    'ONLINE',
    0
)
on conflict (node_key) do nothing;
