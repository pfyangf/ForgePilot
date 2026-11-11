package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("pmo_proposal_decision")
public class PmoProposalDecision {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String projectId;
    private String requirementId;
    private String proposalId;
    private String fingerprint;
    private String kind;
    private String taskType;
    private String defectId;
    private String decision;
    private String comment;
    private String createdTaskId;
    private String actorUserId;
    private Instant createdAt;
}
