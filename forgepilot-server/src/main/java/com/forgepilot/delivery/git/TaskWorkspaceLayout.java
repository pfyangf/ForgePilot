package com.forgepilot.delivery.git;

import java.util.regex.Pattern;

/** 控制面本机/开发用的任务工作区路径布局（与执行面约定一致）。不用 Git Worktree。 */
public final class TaskWorkspaceLayout {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Pattern SAFE_DIR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    public static final String AGENT_HOME_SEGMENT = ".agent-home";

    public String taskRoot(String storageRoot, String taskId) {
        return strip(storageRoot) + "/workspaces/" + require(SAFE_ID, taskId, "非法任务标识");
    }

    public String checkout(String taskRoot, String localDirectoryName) {
        if (!SAFE_DIR.matcher(localDirectoryName == null ? "" : localDirectoryName).matches()
                || AGENT_HOME_SEGMENT.equals(localDirectoryName)) {
            throw new IllegalArgumentException("非法检出目录名");
        }
        return strip(taskRoot) + "/" + localDirectoryName;
    }

    public String agentHome(String taskRoot) {
        return strip(taskRoot) + "/" + AGENT_HOME_SEGMENT;
    }

    public String mirror(String storageRoot, String repositoryId) {
        return strip(storageRoot) + "/mirrors/" + require(SAFE_ID, repositoryId, "非法仓库标识") + ".git";
    }

    private static String require(Pattern pattern, String value, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String strip(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("缺少存储根路径");
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
