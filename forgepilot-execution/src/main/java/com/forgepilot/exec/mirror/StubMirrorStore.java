package com.forgepilot.exec.mirror;

import org.springframework.stereotype.Component;

@Component
public class StubMirrorStore implements MirrorStore {

    @Override
    public void fetch(String repositoryId) {
        throw new UnsupportedOperationException("bare mirror 尚未实现");
    }
}
