package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement_release")
public class RequirementRelease {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String version;
    private String notes;
    private String releasedBy;
    private Instant releasedAt;
}
