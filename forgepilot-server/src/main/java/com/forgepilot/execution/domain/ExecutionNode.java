package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("execution_node")
public class ExecutionNode {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String nodeKey;
    private Integer capacity;
    /** JSON 文本；MyBatis 按 varchar/jsonb 读写。 */
    private String labels;
    private String status;
    private Instant lastHeartbeatAt;
    private Integer activeSessions;
}
