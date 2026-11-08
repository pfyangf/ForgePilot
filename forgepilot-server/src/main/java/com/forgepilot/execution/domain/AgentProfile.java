package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 租户编码工具目录（agent_profile）。 */
@Getter
@Setter
@TableName("agent_profile")
public class AgentProfile {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String name;
    private String agentType;
    private String version;
    private String launchCommand;
    private String modelProviderId;
    private String modelId;
    private Boolean interactiveInput;
    private Boolean nativePause;
    private Boolean interrupt;
    private Boolean eventStream;
    private Boolean toolEvents;
    private Boolean sessionResume;
    private Boolean structuredResult;
    private Integer timeoutSeconds;
    private Boolean enabled;
    private String createdBy;
    private String updatedBy;
}
