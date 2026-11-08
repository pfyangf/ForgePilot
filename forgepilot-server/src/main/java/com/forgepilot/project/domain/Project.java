package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("project")
public class Project {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String name;
    private String code;
    private String description;
    private String techStack;
    private String status;
    private String defaultAgentProfileId;
    private String defaultModelProviderId;
    private String defaultModelId;
    private Integer feedbackLimit;
    private String buildCommand;
    private String testCommand;
    private Integer verificationTimeoutSeconds;
}
