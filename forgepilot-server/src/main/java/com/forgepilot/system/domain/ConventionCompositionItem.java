package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("convention_composition_item")
public class ConventionCompositionItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String compositionId;
    private String guidanceItemId;
    private String scope;
    private String title;
    private String body;
    private String contentHash;
    private Instant createdAt;
}
