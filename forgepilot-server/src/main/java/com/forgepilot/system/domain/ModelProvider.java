package com.forgepilot.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型目录条目。secretId 指向密文，本对象没有长期密钥字段。
 * extraConfig 存模型代码等扩展信息，例如 {"models":[{"code":"gpt-4o","name":"GPT-4o"}]}。
 */
@Getter
@Setter
@TableName(value = "model_provider", autoResultMap = true)
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraConfig;
    private String createdBy;
    private String updatedBy;
}
