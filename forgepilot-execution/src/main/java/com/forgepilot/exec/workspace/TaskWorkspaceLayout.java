package com.forgepilot.exec.workspace;

import java.util.regex.Pattern;

/**
 * 每个任务一个目录。项目的各个仓库克隆到该目录的稳定子目录。
 * Agent 家目录在任务目录内，不使用节点级共享家目录。
 * mirror 路径按仓库分开，可供只读 {@code --reference}，不是可写工作副本。
 */
public final class TaskWorkspaceLayout {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Pattern SAFE_DIR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    public static final String AGENT_HOME_SEGMENT = ".agent-home";

    public String taskRoot(String storageRoot, String taskId) {
        return stripTrailingSlash(storageRoot) + "/workspaces/" + require(SAFE_ID, taskId, "非法任务标识");
    }

    public String checkout(String taskRoot, String localDirectoryName) {
        if (!SAFE_DIR.matcher(localDirectoryName == null ? "" : localDirectoryName).matches()
                || AGENT_HOME_SEGMENT.equals(localDirectoryName)) {
            throw new IllegalArgumentException("非法检出目录名");
        }
        return stripTrailingSlash(taskRoot) + "/" + localDirectoryName;
    }

    public String agentHome(String taskRoot) {
        return stripTrailingSlash(taskRoot) + "/" + AGENT_HOME_SEGMENT;
    }

    public String mirror(String storageRoot, String repositoryId) {
        return stripTrailingSlash(storageRoot) + "/mirrors/" + require(SAFE_ID, repositoryId, "非法仓库标识") + ".git";
    }

    private static String require(Pattern pattern, String value, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("缺少存储根路径");
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
