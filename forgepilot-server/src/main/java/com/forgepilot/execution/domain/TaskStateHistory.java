package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("task_state_history")
public class TaskStateHistory {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String sessionId;
    private String actorId;
    private Instant createdAt;
}
