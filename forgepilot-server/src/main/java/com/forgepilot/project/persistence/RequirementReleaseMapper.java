package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.RequirementRelease;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequirementReleaseMapper extends BaseMapper<RequirementRelease> {
}
