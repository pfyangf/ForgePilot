package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_session")
public class AgentSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String agentProfileId;
    private String agentType;
    private String status;
    private String modelProviderId;
    private String modelId;
    private String nodeId;
    private String workspaceId;
}
