package com.forgepilot.exec.agent;

/**
 * 执行节点上的 Agent 适配器。MVP 选定 Codex CLI，其他适配器只留端口。
 * 上下文里没有 Provider 的长期密钥。
 */
public interface AgentAdapter {

    AgentKind kind();

    AgentCapabilities capabilities();

    AdapterMaturity maturity();

    void start(AgentExecutionContext context);

    void sendMessage(String sessionId, String message);

    void cancel(String sessionId);

    enum AgentKind {
        CODEX_CLI,
        OPENHANDS_AGENT_SERVER,
        ACP
    }

    enum AdapterMaturity {
        /** MVP 选定的运行时（Codex CLI）。 */
        MVP,
        /** 旧名，兼容测试。 */
        @Deprecated
        MVP_STUB,
        /** 只保留端口，不能被选为当前运行时。 */
        RESERVED
    }

    record AgentCapabilities(
            boolean interactiveInput,
            boolean nativePause,
            boolean interrupt,
            boolean eventStream,
            boolean toolEvents,
            boolean sessionResume,
            boolean structuredResult) {
    }

    record AgentExecutionContext(
            String taskId,
            String sessionId,
            String workspacePath,
            String agentHomePath,
            String modelProxyBaseUrl,
            String shortLivedModelToken,
            String modelId) {
        public AgentExecutionContext(
                String taskId,
                String sessionId,
                String workspacePath,
                String agentHomePath,
                String modelProxyBaseUrl,
                String shortLivedModelToken) {
            this(taskId, sessionId, workspacePath, agentHomePath, modelProxyBaseUrl, shortLivedModelToken, null);
        }
    }
}
