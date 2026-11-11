package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement_release_item")
public class RequirementReleaseItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String releaseId;
    private String requirementId;
}
