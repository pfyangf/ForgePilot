package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.ProjectMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    @Select("select project_id from project_member where user_id = CAST(#{userId} AS uuid)")
    List<String> findProjectIdsByUserId(String userId);
}
