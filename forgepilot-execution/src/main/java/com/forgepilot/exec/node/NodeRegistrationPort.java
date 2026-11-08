package com.forgepilot.exec.node;

/** 向控制面注册节点与心跳的端口。骨架不发起调用。 */
public interface NodeRegistrationPort {

    void register(String nodeKey, int capacity);

    void heartbeat(String nodeKey, int activeSessions);
}
