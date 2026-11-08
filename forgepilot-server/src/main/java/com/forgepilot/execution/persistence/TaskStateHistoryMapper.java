package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.TaskStateHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskStateHistoryMapper extends BaseMapper<TaskStateHistory> {
}
