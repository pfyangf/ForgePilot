package com.forgepilot.exec.collector;

import org.springframework.stereotype.Component;

@Component
public class StubArtifactCollector implements ArtifactCollector {

    @Override
    public void upload(String taskId, String sessionId) {
        throw new UnsupportedOperationException("产物上传尚未实现");
    }
}
