package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement_release_target")
public class RequirementReleaseTarget {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String releaseId;
    private String projectRepositoryId;
    private String sourceBranch;
    private String targetBranch;
    private String mergeSha;
    private String status;
    private String message;
    private Instant createdAt;
}
