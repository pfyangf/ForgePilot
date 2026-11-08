package com.forgepilot.exec.agent;

import org.springframework.stereotype.Component;

/**
 * Codex CLI 适配器占位。正式实现时在任务自己的容器和家目录里运行 CLI，
 * 模型入口只指向会话级 Responses 代理。本类不启动进程，也不读取 Provider 密钥。
 */
@Component
public class CodexCliAgentAdapter implements AgentAdapter {

    public static final String INTENDED_ENTRY = "codex exec";

    public static final AgentCapabilities CAPABILITIES = new AgentCapabilities(
            false,
            false,
            true,
            true,
            true,
            true,
            true);

    @Override
    public AgentKind kind() {
        return AgentKind.CODEX_CLI;
    }

    @Override
    public AgentCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public AdapterMaturity maturity() {
        return AdapterMaturity.MVP_STUB;
    }

    @Override
    public void start(AgentExecutionContext context) {
        throw unsupported();
    }

    @Override
    public void sendMessage(String sessionId, String message) {
        throw unsupported();
    }

    @Override
    public void cancel(String sessionId) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Codex CLI 适配器尚未实现，不会启动进程");
    }
}
