package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 长期密钥密文。不得进入 API 响应或 Agent 容器。 */
@Getter
@Setter
@TableName("integration_secret")
public class IntegrationSecret {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerType;
    private String ownerId;
    private String purpose;
    private byte[] ciphertext;
    private Integer keyVersion;
    private String createdBy;
    private String updatedBy;
}
