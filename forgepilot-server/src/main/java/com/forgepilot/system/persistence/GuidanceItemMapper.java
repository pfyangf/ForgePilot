package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.domain.GuidanceItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuidanceItemMapper extends BaseMapper<GuidanceItem> {
}
