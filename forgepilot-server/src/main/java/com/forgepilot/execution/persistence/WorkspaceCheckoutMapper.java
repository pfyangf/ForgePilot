package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.WorkspaceCheckout;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkspaceCheckoutMapper extends BaseMapper<WorkspaceCheckout> {
}
