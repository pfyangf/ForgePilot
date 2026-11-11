package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 领域里的研发任务。表名是 task。任务不绑定单一仓库，工作区会检出项目的全部仓库。
 */
@Getter
@Setter
@TableName("task")
public class CodingTask {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String requirementId;
    private String requirementVersionId;
    private String code;
    private String title;
    private String description;
    private String taskType;
    private String priority;
    private String branchName;
    private String status;
    private String ownerId;
    private String agentProfileId;
    private String modelProviderId;
    private String modelId;
    private String predecessorTaskId;
    private String defectId;
    private String createdBy;
    private java.time.Instant queuedAt;
    /** 进入 COMPLETED/FAILED/CANCELLED 的时间；工作区保留宽限以此为准 */
    private java.time.Instant terminalAt;
}
