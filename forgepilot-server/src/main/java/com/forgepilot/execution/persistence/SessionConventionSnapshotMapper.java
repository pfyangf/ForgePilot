package com.forgepilot.execution.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.execution.domain.SessionConventionSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionConventionSnapshotMapper extends BaseMapper<SessionConventionSnapshot> {
}
