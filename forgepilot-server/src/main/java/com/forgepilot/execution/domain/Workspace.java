package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace")
public class Workspace {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String nodeId;
    private String rootPath;
    private String agentHomePath;
    private String status;
    private String createdBy;
    private java.time.Instant createdAt;
}
