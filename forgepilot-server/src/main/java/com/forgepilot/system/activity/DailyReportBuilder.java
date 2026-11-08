package com.forgepilot.system.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从一天内的操作记录归纳日报。待我处理的存量由调用方传入当前任务状态，不从页面浏览日志推断。
 */
public final class DailyReportBuilder {

    private DailyReportBuilder() {
    }

    public static DailyReport build(List<ActivityEntry> logs, LocalDate day, ZoneId zone, List<WaitingItem> waiting) {
        Map<String, Counts> byUser = new LinkedHashMap<>();
        Map<String, Counts> byProject = new LinkedHashMap<>();
        for (ActivityEntry entry : logs) {
            if (!day.equals(LocalDate.ofInstant(entry.occurredAt(), zone)) || !"SUCCESS".equals(entry.result())) {
                continue;
            }
            if (entry.actorUserId() != null) {
                accumulate(byUser, entry.actorUserId(), entry.action());
            }
            if (entry.projectId() != null) {
                accumulate(byProject, entry.projectId(), entry.action());
            }
        }
        return new DailyReport(day, byUser, byProject, List.copyOf(waiting));
    }

    private static void accumulate(Map<String, Counts> target, String key, String action) {
        Counts counts = target.computeIfAbsent(key, ignored -> new Counts(0, 0, 0, 0, 0, 0));
        target.put(key, counts.plus(action));
    }

    public record ActivityEntry(
            String actorUserId,
            String projectId,
            Instant occurredAt,
            String action,
            String result) {
    }

    public record WaitingItem(String userId, String projectId, String taskId, String reason) {
    }

    public record Counts(
            int createdRequirements,
            int createdTasks,
            int progressed,
            int completed,
            int approved,
            int blocked) {

        private Counts plus(String action) {
            return switch (action) {
                case "REQUIREMENT_CREATED" -> new Counts(createdRequirements + 1, createdTasks, progressed, completed, approved, blocked);
                case "TASK_CREATED" -> new Counts(createdRequirements, createdTasks + 1, progressed, completed, approved, blocked);
                case "TASK_PROGRESSED" -> new Counts(createdRequirements, createdTasks, progressed + 1, completed, approved, blocked);
                case "TASK_COMPLETED" -> new Counts(createdRequirements, createdTasks, progressed, completed + 1, approved, blocked);
                case "TASK_APPROVED" -> new Counts(createdRequirements, createdTasks, progressed, completed, approved + 1, blocked);
                case "TASK_BLOCKED" -> new Counts(createdRequirements, createdTasks, progressed, completed, approved, blocked + 1);
                default -> this;
            };
        }
    }

    public record DailyReport(
            LocalDate day,
            Map<String, Counts> byUser,
            Map<String, Counts> byProject,
            List<WaitingItem> waitingOnUsers) {
    }
}
