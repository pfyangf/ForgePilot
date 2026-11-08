package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型目录条目。secretId 指向密文，本对象没有长期密钥字段。
 */
@Getter
@Setter
@TableName("model_provider")
public class ModelProvider {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String name;
    private String providerType;
    private String baseUrl;
    private String secretId;
    private Boolean enabled;
    private String apiVersion;
    private String upstreamWireApi;
    private String translationGateway;
    private String createdBy;
    private String updatedBy;
}
