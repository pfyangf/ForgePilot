package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.AgentEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentEventMapper extends BaseMapper<AgentEvent> {
}
