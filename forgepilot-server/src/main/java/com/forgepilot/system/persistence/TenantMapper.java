package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
