package com.forgepilot.delivery.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("verification_run")
public class VerificationRun {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String workspaceId;
    private String command;
    private Integer timeoutSeconds;
    private Integer exitCode;
    private Integer attempt;
    private Instant createdAt;
    private Instant finishedAt;
}
