package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 只追加的操作记录。没有更新字段，实体也不保存密钥。 */
@Getter
@Setter
@TableName(value = "activity_log", autoResultMap = true)
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
    private String result;
}
