package com.forgepilot.project.domain;

/**
 * 仓库在任务工作区中的稳定子目录名。禁止路径穿越，也避免与任务私有的 Agent 家目录重名。
 */
public final class LocalDirectoryName {

    public static final String AGENT_HOME_SEGMENT = ".agent-home";
    private static final java.util.regex.Pattern SAFE =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private LocalDirectoryName() {
    }

    public static boolean isSafe(String name) {
        return name != null && SAFE.matcher(name).matches() && !AGENT_HOME_SEGMENT.equals(name);
    }

    public static String require(String name) {
        if (!isSafe(name)) {
            throw new IllegalArgumentException("非法检出目录名");
        }
        return name;
    }
}
