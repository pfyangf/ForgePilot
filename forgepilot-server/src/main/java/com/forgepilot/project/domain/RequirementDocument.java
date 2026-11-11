package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement_document")
public class RequirementDocument {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String requirementId;
    private String kind;
    private String title;
    private String body;
    private Integer versionNo;
    @TableField("current_flag")
    private Boolean currentFlag;
    private Boolean pendingReview;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
