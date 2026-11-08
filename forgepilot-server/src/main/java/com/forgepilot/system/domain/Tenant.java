package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tenant")
public class Tenant {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String code;
    private String status;
}
