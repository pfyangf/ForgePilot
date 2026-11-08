package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.Workspace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkspaceMapper extends BaseMapper<Workspace> {
}
