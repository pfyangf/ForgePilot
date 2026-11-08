package com.forgepilot.delivery.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 在工作区根目录执行项目配置的验证命令。
 * 本机实现用子进程；后续可换成「无 Agent CLI、无模型凭证」的验证容器。
 */
public interface VerificationRunner {

    VerificationResult run(Path workspaceRoot, List<String> commands, Duration timeout);

    record VerificationResult(boolean passed, int exitCode, String output, List<String> commands) {
    }
}
