package com.forgepilot.exec.agent;

import org.springframework.stereotype.Component;

/**
 * OpenHands Agent Server（REST/WebSocket）的预留端口。
 * 不把 OpenHands 产品分叉进本仓库，当前也不可被选为运行时。
 */
@Component
public class OpenHandsAgentServerAdapter implements AgentAdapter {

    private static final AgentCapabilities NONE = new AgentCapabilities(false, false, false, false, false, false, false);

    @Override
    public AgentKind kind() {
        return AgentKind.OPENHANDS_AGENT_SERVER;
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
        return new UnsupportedOperationException("OpenHands Agent Server 适配器只留端口");
    }
}
