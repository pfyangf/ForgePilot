package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.AgentProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfile> {
}
