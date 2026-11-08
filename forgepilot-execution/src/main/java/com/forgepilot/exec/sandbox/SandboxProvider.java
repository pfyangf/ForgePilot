package com.forgepilot.exec.sandbox;

/** 沙箱端口。Agent 容器和验证容器共用这一抽象，用角色区分。 */
public interface SandboxProvider {

    String kind();

    SandboxHandle create(SandboxRequest request);

    enum SandboxRole {
        AGENT,
        VERIFICATION
    }

    record SandboxRequest(
            String taskId,
            String sessionId,
            String workspacePath,
            String agentHomePath,
            String networkName,
            SandboxRole role) {
    }

    record SandboxHandle(String taskId, String networkName, SandboxRole role) {
    }
}
