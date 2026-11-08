package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 只追加的操作记录。没有更新字段，实体也不保存密钥。 */
@Getter
@Setter
@TableName("activity_log")
public class ActivityLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String actorKind;
    private String actorUserId;
    private String actorService;
    private Instant occurredAt;
    private String action;
    private String targetType;
    private String targetId;
    private String projectId;
    private String requirementId;
    private String taskId;
    private String requestId;
    private String summary;
    private String result;
}
