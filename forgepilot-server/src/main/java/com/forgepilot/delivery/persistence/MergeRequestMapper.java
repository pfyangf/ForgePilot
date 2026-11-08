package com.forgepilot.delivery.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.delivery.domain.MergeRequestRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MergeRequestMapper extends BaseMapper<MergeRequestRecord> {
}
