package com.forgepilot.delivery.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 一条 Draft MR。同一任务在每个有改动的仓库上至多一条开放记录。
 */
@Getter
@Setter
@TableName("merge_request")
public class MergeRequestRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String projectRepositoryId;
    private String providerType;
    private String externalId;
    private String url;
    private String branchName;
    private String headSha;
    private String idempotencyKey;
    private String state;
}
