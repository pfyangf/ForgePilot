package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("project_member")
public class ProjectMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String userId;
    private Boolean projectAdmin;
    private String createdBy;
    private String updatedBy;
}
