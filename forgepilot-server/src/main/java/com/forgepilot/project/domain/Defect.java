package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("defect")
public class Defect {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String requirementId;
    private String taskId;
    private String code;
    private String title;
    private String description;
    private String severity;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
