package com.forgepilot.execution.branch;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * 工作分支默认名按需求编号生成，例如 {@code fp/REQ-DEMO99-3}。
 * 若与已有任务冲突，则追加任务编号：{@code fp/REQ-DEMO99-3-TASK-DEMO99-7}。
 * Agent 不得自行发明分支名，必须使用这里选定的名称。
 */
public final class WorkingBranchNames {

    public static final String PREFIX = "fp/";
    public static final int MAX_LENGTH = 200;

    private WorkingBranchNames() {}

    public static String fromRequirementCode(String requirementCode) {
        return PREFIX + sanitizeSegment(requirementCode);
    }

    public static String withTaskSuffix(String requirementCode, String taskCode) {
        return PREFIX + sanitizeSegment(requirementCode) + "-" + sanitizeSegment(taskCode);
    }

    public static String defaultName(String requirementCode, String taskCode, Collection<String> taken) {
        String primary = fromRequirementCode(requirementCode);
        Set<String> existing = taken == null ? Set.of() : Set.copyOf(taken);
        if (!existing.contains(primary)) {
            return primary;
        }
        String suffixed = withTaskSuffix(requirementCode, taskCode);
        if (!existing.contains(suffixed)) {
            return suffixed;
        }
        int i = 2;
        String candidate = suffixed + "-" + i;
        while (existing.contains(candidate)) {
            i++;
            candidate = suffixed + "-" + i;
        }
        return candidate;
    }

    /** 清理用户输入的完整分支名，保留一个 {@code fp/} 前缀（若本身没有其它合法前缀也可不以 fp 开头）。 */
    public static String sanitizeFull(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("工作分支名不能为空");
        }
        String trimmed = raw.trim().replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String[] parts = trimmed.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String clean = sanitizeSegment(part);
            if (clean.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(clean);
        }
        String result = out.toString();
        if (result.isBlank()) {
            throw new IllegalArgumentException("工作分支名无效");
        }
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH);
        }
        return result;
    }

    public static String sanitizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "branch";
        }
        String value = raw.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        String cleaned = out.toString().replaceAll("-{2,}", "-");
        cleaned = trimmed(cleaned, '-');
        cleaned = trimmed(cleaned, '.');
        if (cleaned.isBlank()) {
            return "branch";
        }
        return cleaned.toUpperCase(Locale.ROOT).equals(cleaned) || cleaned.contains("REQ-") || cleaned.contains("TASK-")
                ? cleaned
                : cleaned;
    }

    private static String trimmed(String value, char ch) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ch) {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ch) {
            end--;
        }
        return value.substring(start, end);
    }
}
