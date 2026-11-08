package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.IntegrationSecret;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationSecretMapper extends BaseMapper<IntegrationSecret> {
}
