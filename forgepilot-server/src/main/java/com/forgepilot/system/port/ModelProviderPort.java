package com.forgepilot.system.port;

/**
 * 平台自己的模型调用端口。调用方只传 Provider 标识，不传长期密钥。
 * 实现类在服务端读取密文。Agent 容器不走这个端口。
 */
public interface ModelProviderPort {

    ModelProviderView get(String providerId);

    void complete(PlatformModelRequest request);

    record PlatformModelRequest(String providerId, String modelId, String prompt) {
    }

    record ModelProviderView(
            String id,
            String name,
            String providerType,
            String baseUrl,
            String upstreamWireApi,
            boolean translationGatewayConfigured,
            boolean enabled,
            boolean eligibleForCodex) {
    }
}
