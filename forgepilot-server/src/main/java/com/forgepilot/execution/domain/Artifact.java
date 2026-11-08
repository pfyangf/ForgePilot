package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("artifact")
public class Artifact {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String sessionId;
    private String projectRepositoryId;
    private String kind;
    private String objectKey;
    private String checksum;
    private Long byteSize;
    private Instant createdAt;
}
