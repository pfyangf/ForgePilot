package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement")
public class Requirement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String code;
    private String title;
    private String description;
    private String background;
    private String scope;
    private String acceptance;
    private String status;
    private Boolean statusLocked;
    private String priority;
    private String ownerId;
    private String createdBy;
}
