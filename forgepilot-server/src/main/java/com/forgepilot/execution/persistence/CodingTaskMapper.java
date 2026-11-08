package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.CodingTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CodingTaskMapper extends BaseMapper<CodingTask> {
}
