package com.forgepilot.execution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("execution_node")
public class ExecutionNode {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String nodeKey;
    private Integer capacity;
    private String status;
}
