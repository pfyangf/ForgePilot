package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("retention_policy")
public class RetentionPolicy {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String scope;
    private String tenantId;
    private Integer workspaceRetentionDays;
    private Integer artifactRetentionDays;
}
