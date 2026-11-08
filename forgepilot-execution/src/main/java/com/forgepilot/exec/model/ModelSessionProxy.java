package com.forgepilot.exec.model;

/**
 * 执行节点上的会话级模型代理。对 Agent 只暴露 Responses。
 * 签发结果里的令牌是短时的，不是 Provider 长期密钥。
 * 上游若只说 Chat Completions 或原生 API，由本代理或其后的翻译网关转换。
 */
public interface ModelSessionProxy {

    String exposedWireApi();

    ModelSessionCredential issue(ModelSessionRequest request);

    void revoke(String sessionId);

    record ModelSessionRequest(String sessionId, String providerId, String modelId) {
    }

    record ModelSessionCredential(String sessionId, String providerId, String baseUrl, String shortLivedToken) {
    }
}
