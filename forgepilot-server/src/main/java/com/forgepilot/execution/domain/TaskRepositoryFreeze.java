package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务启动（创建）时冻结的仓库集合条目。表名 task_repository。
 * 之后项目增删仓库不影响本任务。
 */
@Getter
@Setter
@TableName("task_repository")
public class TaskRepositoryFreeze {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String projectRepositoryId;
    private String baseBranch;
    private String workingBranch;
    private String baseSha;
    private String deliveryState;
    private Instant frozenAt;
}
