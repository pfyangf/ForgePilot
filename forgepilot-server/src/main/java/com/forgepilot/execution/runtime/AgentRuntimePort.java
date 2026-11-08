package com.forgepilot.execution.runtime;

import java.util.List;
import java.util.Map;

/**
 * 控制面到执行节点的 Agent 运行时端口。长期 Provider Key 只经此内部通道交给执行侧代理，
 * 绝不进入 Agent 环境或前端响应。
 */
public interface AgentRuntimePort {

    StartResult start(StartCommand command);

    void cancel(String sessionId);

    void sendInput(String sessionId, String message);

    void revokeModelToken(String sessionId);

    List<String> tailLogs(String sessionId, int limit);

    record StartCommand(
            String taskId,
            String sessionId,
            String workspacePath,
            String agentHomePath,
            String providerId,
            String modelId,
            String upstreamBaseUrl,
            String upstreamApiKey,
            String upstreamWireApi) {
    }

    record StartResult(
            String sessionId,
            String runtimeStatus,
            String modelProxyBaseUrl,
            String wireApi,
            Map<String, Object> agentFacingConfig,
            boolean interactiveInput,
            String errorCode,
            String errorMessage) {
        public boolean failed() {
            return errorCode != null && !errorCode.isBlank();
        }
    }
}
