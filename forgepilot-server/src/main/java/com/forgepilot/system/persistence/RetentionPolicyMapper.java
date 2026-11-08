package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.RetentionPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RetentionPolicyMapper extends BaseMapper<RetentionPolicy> {
}
