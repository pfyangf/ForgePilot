package com.forgepilot.system.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("outbox")
public class OutboxEvent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String sessionId;
    /** JSON 文本 */
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;
}
