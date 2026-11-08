package com.forgepilot.execution.stream;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StubLogArchive implements LogArchivePort {

    private final List<ArchiveResult> uploaded = new ArrayList<>();

    @Override
    public void archive(String taskId, String sessionId, String objectKeyHint) {
        uploaded.add(new ArchiveResult(
                objectKeyHint == null ? "logs/" + taskId + "/" + sessionId + ".log" : objectKeyHint, false));
    }

    public List<ArchiveResult> uploaded() {
        return List.copyOf(uploaded);
    }
}
