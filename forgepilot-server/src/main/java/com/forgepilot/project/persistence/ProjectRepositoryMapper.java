package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.ProjectRepository;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectRepositoryMapper extends BaseMapper<ProjectRepository> {
}
