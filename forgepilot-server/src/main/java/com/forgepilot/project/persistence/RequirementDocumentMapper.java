package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.RequirementDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequirementDocumentMapper extends BaseMapper<RequirementDocument> {
}
