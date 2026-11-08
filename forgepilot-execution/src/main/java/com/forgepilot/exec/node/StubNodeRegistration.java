package com.forgepilot.exec.node;

import org.springframework.stereotype.Component;

@Component
public class StubNodeRegistration implements NodeRegistrationPort {

    @Override
    public void register(String nodeKey, int capacity) {
        throw new UnsupportedOperationException("节点注册尚未实现");
    }

    @Override
    public void heartbeat(String nodeKey, int activeSessions) {
        throw new UnsupportedOperationException("节点心跳尚未实现");
    }
}
