package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("review")
public class Review {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String subjectType;
    private String subjectId;
    private String decision;
    private String comment;
    private String reviewerId;
    private Instant createdAt;
}
