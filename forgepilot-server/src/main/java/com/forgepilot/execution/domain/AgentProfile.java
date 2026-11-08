package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_profile")
public class AgentProfile {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String agentType;
    private String modelProviderId;
    private String modelId;
    private Boolean interactiveInput;
    private Boolean nativePause;
    private Boolean enabled;
}
