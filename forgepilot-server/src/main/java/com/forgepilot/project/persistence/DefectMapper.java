package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.Defect;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DefectMapper extends BaseMapper<Defect> {
}
