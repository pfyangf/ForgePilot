insert into sys_menu_permission (id, role_id, menu_code, name) values
    ('00000000-0000-4000-8000-000000000104', '00000000-0000-4000-8000-000000000001', 'nodes', '执行节点');

insert into sys_menu_permission (id, role_id, menu_code, name)
select '00000000-0000-4000-8000-000000000205',
       '00000000-0000-4000-8000-000000000002',
       'catalog',
       '模型与工具'
where not exists (
    select 1 from sys_menu_permission
    where role_id = '00000000-0000-4000-8000-000000000002'
      and menu_code = 'catalog'
);
