package com.forgepilot.project.workbench;

public enum WorkbenchScope {
    MINE,
    WAITING,
    MEMBER;

    public static WorkbenchScope from(String raw) {
        if (raw == null || raw.isBlank() || "mine".equalsIgnoreCase(raw)) {
            return MINE;
        }
        if ("waiting".equalsIgnoreCase(raw)) {
            return WAITING;
        }
        if ("member".equalsIgnoreCase(raw)) {
            return MEMBER;
        }
        throw new IllegalArgumentException("未知工作台范围: " + raw);
    }
}
