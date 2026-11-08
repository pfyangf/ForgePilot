package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent Session。与 Task 分离：任务可有多次 Session；状态权威在 PostgreSQL。
 */
@Getter
@Setter
@TableName("agent_session")
public class AgentSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String agentProfileId;
    private String agentType;
    private String agentVersion;
    private String status;
    private String modelProviderId;
    private String modelId;
    private String nodeId;
    private String workspaceId;
    private String containerRef;
    private Instant startedAt;
    private Instant endedAt;
    private String errorSummary;
    private Long lastEventSeq;
    private Integer tokenUsage;
    private String conventionSetHash;
    private String createdBy;
}
