package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.ExecutionNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExecutionNodeMapper extends BaseMapper<ExecutionNode> {
}
