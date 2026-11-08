package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.Role;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
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

    @Select("select id, code, name from sys_role where code = #{code}")
    Role findByCode(String code);

    @Insert("insert into sys_user_role (user_id, role_id) values (#{userId}, #{roleId})")
    int insertUserRole(@org.apache.ibatis.annotations.Param("userId") String userId,
            @org.apache.ibatis.annotations.Param("roleId") String roleId);
}
