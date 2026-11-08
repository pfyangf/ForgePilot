package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_checkout")
public class WorkspaceCheckout {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String workspaceId;
    private String projectRepositoryId;
    private String localDirectoryName;
    private String clonePath;
    private String baseSha;
    private String branchName;
    private String status;
}
