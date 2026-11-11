package com.forgepilot.project.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgepilot.project.domain.Review;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
