package com.forgepilot.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.system.outbox.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxEvent> {
}
