package com.forgepilot.delivery.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 本机 git 操作。凭证通过临时 GIT_ASKPASS 脚本注入，禁止写入 remote URL。
 * UID/GID：容器化阶段需使非 root 用户与宿主机工作区属主一致；本机测试沿用当前用户即可。
 */
@Component
public class LocalGitSupport {

    public void mirrorFetch(Path mirrorPath, String cloneUrl, String askPassToken) {
        try {
            if (!Files.exists(mirrorPath)) {
                Files.createDirectories(mirrorPath.getParent());
                run(List.of("git", "clone", "--mirror", cloneUrl, mirrorPath.toString()), null, askPassToken);
            } else {
                run(List.of("git", "fetch", "--all", "--prune"), mirrorPath, askPassToken);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("准备 bare mirror 失败", ex);
        }
    }

    public void cloneCheckout(
            Path mirrorPath, Path checkoutPath, String cloneUrl, String baseBranch, String askPassToken) {
        try {
            if (Files.exists(checkoutPath.resolve(".git"))) {
                return;
            }
            if (Files.exists(checkoutPath)) {
                // 不完整目录（无 .git）则清掉后重克隆，保证 retry 同一路径可恢复
                deleteRecursive(checkoutPath);
            }
            Files.createDirectories(checkoutPath.getParent());
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.add("clone");
            if (mirrorPath != null && Files.exists(mirrorPath)) {
                cmd.add("--reference");
                cmd.add(mirrorPath.toString());
            }
            cmd.add("--branch");
            cmd.add(baseBranch);
            cmd.add("--single-branch");
            cmd.add(cloneUrl);
            cmd.add(checkoutPath.toString());
            run(cmd, null, askPassToken);
        } catch (IOException ex) {
            throw new IllegalStateException("克隆检出失败", ex);
        }
    }

    public String revParse(Path checkout, String rev) {
        return run(List.of("git", "rev-parse", rev), checkout, null).stdout().trim();
    }

    public boolean hasChangesSince(Path checkout, String baseSha) {
        CommandResult result = run(List.of("git", "status", "--porcelain"), checkout, null);
        if (!result.stdout().isBlank()) {
            return true;
        }
        CommandResult diff =
                run(List.of("git", "diff", "--name-only", baseSha + "..HEAD"), checkout, null);
        return !diff.stdout().isBlank();
    }

    public List<String> changedFilesSince(Path checkout, String baseSha) {
        CommandResult diff =
                run(List.of("git", "diff", "--name-status", baseSha + "..HEAD"), checkout, null);
        List<String> lines = new ArrayList<>();
        for (String line : diff.stdout().split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        // 也包括工作区未提交改动
        CommandResult porcelain = run(List.of("git", "status", "--porcelain"), checkout, null);
        for (String line : porcelain.stdout().split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    public void commitAllIfDirty(Path checkout, String message) {
        run(List.of("git", "add", "-A"), checkout, null);
        CommandResult status = run(List.of("git", "status", "--porcelain"), checkout, null);
        if (status.stdout().isBlank()) {
            return;
        }
        run(List.of("git", "-c", "user.email=forgepilot@local", "-c", "user.name=ForgePilot", "commit", "-m", message),
                checkout,
                null);
    }

    public void checkoutBranch(Path checkout, String branchName) {
        run(List.of("git", "checkout", "-B", branchName), checkout, null);
    }

    public void push(Path checkout, String remote, String branchName, String askPassToken) {
        run(List.of("git", "push", "-u", remote, branchName), checkout, askPassToken);
    }

    public CommandResult run(List<String> command, Path workDir, String askPassToken) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            pb.redirectErrorStream(true);
            Path askPass = null;
            if (askPassToken != null && !askPassToken.isBlank()) {
                askPass = Files.createTempFile("fp-askpass-", ".sh");
                Files.writeString(
                        askPass,
                        "#!/bin/sh\necho '" + askPassToken.replace("'", "'\\''") + "'\n",
                        StandardCharsets.UTF_8);
                askPass.toFile().setExecutable(true);
                pb.environment().put("GIT_ASKPASS", askPass.toString());
                pb.environment().put("GIT_TERMINAL_PROMPT", "0");
                // 不把令牌放进 GIT_URL / remote
            }
            Process process = pb.start();
            boolean finished = process.waitFor(Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS);
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (askPass != null) {
                Files.deleteIfExists(askPass);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git 命令超时: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git 失败 (" + process.exitValue() + "): " + command + "\n" + out);
            }
            return new CommandResult(process.exitValue(), out);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("执行 git 失败: " + command, ex);
        }
    }

    public record CommandResult(int exitCode, String stdout) {
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
