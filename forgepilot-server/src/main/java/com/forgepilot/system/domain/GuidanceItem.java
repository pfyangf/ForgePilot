package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("guidance_item")
public class GuidanceItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String scope;
    private String tenantId;
    private String projectId;
    private String kind;
    private String title;
    private String category;
    private String body;
    private String objectKey;
    private Boolean enabled;
    private Boolean includeInAgent;
    private String contentHash;
    private String createdBy;
    private String updatedBy;
}
