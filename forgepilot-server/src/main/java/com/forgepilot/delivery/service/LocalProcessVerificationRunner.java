package com.forgepilot.delivery.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LocalProcessVerificationRunner implements VerificationRunner {

    @Override
    public VerificationResult run(Path workspaceRoot, List<String> commands, Duration timeout) {
        if (commands == null || commands.isEmpty()) {
            return new VerificationResult(true, 0, "no verification commands configured", List.of());
        }
        StringBuilder output = new StringBuilder();
        int lastExit = 0;
        Duration each = timeout == null ? Duration.ofMinutes(10) : timeout;
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-lc", command);
                pb.directory(workspaceRoot.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(each.toMillis(), TimeUnit.MILLISECONDS);
                String chunk = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                output.append("$ ").append(command).append('\n').append(chunk).append('\n');
                if (!finished) {
                    process.destroyForcibly();
                    return new VerificationResult(false, 124, output + "\n(timeout)", List.copyOf(commands));
                }
                lastExit = process.exitValue();
                if (lastExit != 0) {
                    return new VerificationResult(false, lastExit, output.toString(), List.copyOf(commands));
                }
            } catch (IOException | InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new VerificationResult(false, 1, output + "\n" + ex.getMessage(), List.copyOf(commands));
            }
        }
        return new VerificationResult(true, lastExit, output.toString(), List.copyOf(commands));
    }
}
