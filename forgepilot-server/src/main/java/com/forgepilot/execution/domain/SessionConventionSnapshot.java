package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("session_convention_snapshot")
public class SessionConventionSnapshot {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String sessionId;
    private String guidanceItemId;
    private String scope;
    private String title;
    private String contentHash;
    private Instant createdAt;
}
