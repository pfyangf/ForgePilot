package com.forgepilot.exec.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本机进程运行时。默认剥掉继承环境中的 Provider 长期密钥与基础设施凭证。
 * 若 {@code forgepilot.process.require-sandbox=true}，直接拒绝启动（fail-closed）。
 * 进程模式不具备容器级网络隔离，不得当作生产沙箱。
 */
@Component
public class LocalProcessRuntime implements ProcessRuntime {

    private static final Set<String> ALLOWED_SESSION_KEYS = Set.of(
            "CODEX_API_KEY", "CODEX_HOME", "OPENAI_BASE_URL", "PATH", "HOME", "LANG", "LC_ALL", "TERM", "USER", "LOGNAME");

    private final ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();
    private final boolean requireSandbox;

    public LocalProcessRuntime(
            @Value("${forgepilot.process.require-sandbox:false}") boolean requireSandbox) {
        this.requireSandbox = requireSandbox;
    }

    /** 测试用：无 Spring 时默认不强制沙箱 */
    public LocalProcessRuntime() {
        this(false);
    }

    /** 测试/显式 fail-closed */
    public static LocalProcessRuntime requiringSandbox() {
        return new LocalProcessRuntime(true);
    }

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
        if (requireSandbox) {
            throw new IllegalStateException(
                    "forgepilot.process.require-sandbox=true：进程模式不是生产隔离，已拒绝启动。请使用 ContainerRuntime。");
        }
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
            // ProcessBuilder 默认继承父进程环境——必须剥离 Provider 长期密钥
            stripSensitive(pb.environment());
            if (spec.environment() != null) {
                for (Map.Entry<String, String> e : spec.environment().entrySet()) {
                    if (isForbiddenProviderSecret(e.getKey(), e.getValue())) {
                        throw new IllegalArgumentException(
                                "进程环境禁止注入 Provider 长期 apiKey: " + e.getKey());
                    }
                    pb.environment().put(e.getKey(), e.getValue());
                }
            }
            // 再次剥离，防止 putAll 后残留
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

    /** 暴露给测试：对可变 env map 应用剥离规则 */
    public static void stripSensitive(Map<String, String> env) {
        env.keySet().removeIf(LocalProcessRuntime::isSensitiveKey);
    }

    static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        if (ALLOWED_SESSION_KEYS.contains(key)) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("codex_api_key")) {
            return false; // 会话短时令牌
        }
        return k.contains("postgres")
                || k.contains("rabbit")
                || k.contains("redis")
                || k.contains("minio")
                || k.contains("forgepilot_jwt")
                || k.contains("database_url")
                || k.contains("amqp")
                || k.contains("jdbc")
                || k.equals("openai_api_key")
                || k.contains("anthropic_api_key")
                || (k.contains("api_key") && !k.equals("codex_api_key"))
                || k.contains("api-key")
                || k.endsWith("_secret")
                || k.contains("aws_secret")
                || k.contains("private_key");
    }

    static boolean isForbiddenProviderSecret(String key, String value) {
        if (key == null) {
            return false;
        }
        if ("CODEX_API_KEY".equals(key)) {
            // 会话短时令牌允许；若误传 sk- 长期密钥则拒绝
            return value != null && value.toLowerCase(Locale.ROOT).startsWith("sk-");
        }
        return isSensitiveKey(key);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
