package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 重要事件摘要。禁止按 token 插入。 */
@Getter
@Setter
@TableName("agent_event")
public class AgentEvent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String sessionId;
    private String taskId;
    private Long seq;
    private String eventType;
    private String summary;
    private Instant createdAt;
}
