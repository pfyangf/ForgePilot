package com.forgepilot.exec.agent;

import org.springframework.stereotype.Component;

/** ACP 兼容 Agent 的预留端口。当前不可被选为运行时。 */
@Component
public class AcpAgentAdapter implements AgentAdapter {

    private static final AgentCapabilities NONE = new AgentCapabilities(false, false, false, false, false, false, false);

    @Override
    public AgentKind kind() {
        return AgentKind.ACP;
    }

    @Override
    public AgentCapabilities capabilities() {
        return NONE;
    }

    @Override
    public AdapterMaturity maturity() {
        return AdapterMaturity.RESERVED;
    }

    @Override
    public void start(AgentExecutionContext context) {
        throw reserved();
    }

    @Override
    public void sendMessage(String sessionId, String message) {
        throw reserved();
    }

    @Override
    public void cancel(String sessionId) {
        throw reserved();
    }

    private static UnsupportedOperationException reserved() {
        return new UnsupportedOperationException("ACP 适配器只留端口");
    }
}
