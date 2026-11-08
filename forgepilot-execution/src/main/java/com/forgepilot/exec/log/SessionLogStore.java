package com.forgepilot.exec.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 高频日志本机文件/内存尾部；不走 RabbitMQ。 */
@Component
public class SessionLogStore {

    private final Path root;
    private final ConcurrentHashMap<String, List<String>> memory = new ConcurrentHashMap<>();

    public SessionLogStore(@Value("${forgepilot.storage.root:${java.io.tmpdir}/forgepilot-execution}") String storageRoot) {
        this.root = Path.of(storageRoot, "session-logs");
    }

    public void append(String sessionId, String line) {
        memory.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(line);
        try {
            Files.createDirectories(root);
            Path file = root.resolve(sessionId + ".log");
            Files.writeString(
                    file,
                    line + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // memory remains the source for polls
        }
    }

    public List<String> tail(String sessionId, int limit) {
        List<String> lines = memory.getOrDefault(sessionId, List.of());
        if (lines.isEmpty()) {
            Path file = root.resolve(sessionId + ".log");
            if (Files.exists(file)) {
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    return List.of();
                }
            }
        }
        int from = Math.max(0, lines.size() - Math.max(1, limit));
        return List.copyOf(lines.subList(from, lines.size()));
    }

    public Path logFile(String sessionId) {
        return root.resolve(sessionId + ".log");
    }

    public Map<String, Object> stats(String sessionId) {
        return Map.of("sessionId", sessionId, "lines", memory.getOrDefault(sessionId, List.of()).size());
    }
}
