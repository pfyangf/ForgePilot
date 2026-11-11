package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("analysis_draft")
public class AnalysisDraft {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String requirementId;
    private String requirementVersionId;
    private String body;
    private String suggestedTasks;
    private String confirmedBy;
    private Instant confirmedAt;
    private String createdBy;
    private String updatedBy;
}
