package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 尚无 Agent Session 时也可保存的规约合成快照头。 */
@Getter
@Setter
@TableName("convention_composition")
public class ConventionComposition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String projectId;
    private String compositionHash;
    private String createdBy;
    private Instant createdAt;
}
