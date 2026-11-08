package com.forgepilot.exec.sandbox;

import org.springframework.stereotype.Component;

/** Docker 沙箱占位。不连接 Docker Socket，也不创建容器。 */
@Component
public class DockerSandboxProvider implements SandboxProvider {

    @Override
    public String kind() {
        return "docker";
    }

    @Override
    public SandboxHandle create(SandboxRequest request) {
        throw new UnsupportedOperationException("Docker 沙箱尚未实现");
    }
}
