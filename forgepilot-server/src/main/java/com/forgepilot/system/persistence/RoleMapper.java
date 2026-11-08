package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.Role;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
            select r.id, r.code, r.name
            from sys_role r
            inner join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
            """)
    List<Role> findByUserId(String userId);
}
