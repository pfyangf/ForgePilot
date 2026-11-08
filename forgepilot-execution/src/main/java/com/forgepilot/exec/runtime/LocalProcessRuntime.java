package com.forgepilot.exec.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LocalProcessRuntime implements ProcessRuntime {

    private final ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();

    @Override
    public boolean isCommandAvailable(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("bash", "-lc", "command -v " + shellQuote(command))
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public StartedHandle start(StartSpec spec) {
        try {
            if (spec.workingDirectory() != null) {
                Files.createDirectories(spec.workingDirectory());
            }
            if (spec.stdoutLog() != null) {
                Files.createDirectories(spec.stdoutLog().getParent());
            }
            ProcessBuilder pb = new ProcessBuilder(spec.command());
            if (spec.workingDirectory() != null) {
                pb.directory(spec.workingDirectory().toFile());
            }
            if (spec.environment() != null) {
                pb.environment().putAll(spec.environment());
            }
            // Isolation note: never inject control-plane / DB / broker admin creds into Agent env.
            stripSensitive(pb.environment());
            if (spec.stdoutLog() != null) {
                pb.redirectErrorStream(true);
                pb.redirectOutput(spec.stdoutLog().toFile());
            }
            Process process = pb.start();
            processes.put(spec.handleId(), process);
            return new StartedHandle(spec.handleId(), process.pid());
        } catch (IOException ex) {
            throw new IllegalStateException("启动进程失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void destroy(String handleId) {
        Process process = processes.remove(handleId);
        if (process != null) {
            process.destroyForcibly();
        }
    }

    @Override
    public boolean isAlive(String handleId) {
        Process process = processes.get(handleId);
        return process != null && process.isAlive();
    }

    private static void stripSensitive(Map<String, String> env) {
        env.keySet().removeIf(key -> {
            String k = key.toLowerCase();
            return k.contains("postgres")
                    || k.contains("rabbit")
                    || k.contains("redis")
                    || k.contains("minio")
                    || k.contains("forgepilot_jwt")
                    || k.contains("database_url")
                    || k.contains("amqp");
        });
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
