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

    public static String toMarkdown(DailyReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# 日报 ").append(report.day()).append('\n').append('\n');
        md.append("## 按人员\n\n");
        if (report.byUser().isEmpty()) {
            md.append("（无）\n\n");
        } else {
            md.append("| 用户 | 新建需求 | 新建任务 | 推进 | 完成 | 审核 | 受阻 |\n");
            md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            report.byUser().forEach((user, c) -> md.append("| ")
                    .append(user)
                    .append(" | ")
                    .append(c.createdRequirements())
                    .append(" | ")
                    .append(c.createdTasks())
                    .append(" | ")
                    .append(c.progressed())
                    .append(" | ")
                    .append(c.completed())
                    .append(" | ")
                    .append(c.approved())
                    .append(" | ")
                    .append(c.blocked())
                    .append(" |\n"));
            md.append('\n');
        }
        md.append("## 按项目\n\n");
        if (report.byProject().isEmpty()) {
            md.append("（无）\n\n");
        } else {
            md.append("| 项目 | 新建需求 | 新建任务 | 推进 | 完成 | 审核 | 受阻 |\n");
            md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            report.byProject().forEach((project, c) -> md.append("| ")
                    .append(project)
                    .append(" | ")
                    .append(c.createdRequirements())
                    .append(" | ")
                    .append(c.createdTasks())
                    .append(" | ")
                    .append(c.progressed())
                    .append(" | ")
                    .append(c.completed())
                    .append(" | ")
                    .append(c.approved())
                    .append(" | ")
                    .append(c.blocked())
                    .append(" |\n"));
            md.append('\n');
        }
        md.append("## 仍待处理\n\n");
        if (report.waitingOnUsers().isEmpty()) {
            md.append("（无）\n");
        } else {
            for (WaitingItem item : report.waitingOnUsers()) {
                md.append("- 用户 ")
                        .append(item.userId())
                        .append(" / 项目 ")
                        .append(item.projectId())
                        .append(" / 任务 ")
                        .append(item.taskId())
                        .append("：")
                        .append(item.reason())
                        .append('\n');
            }
        }
        return md.toString();
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
            return switch (normalize(action)) {
                case "REQUIREMENT_CREATED" -> new Counts(
                        createdRequirements + 1, createdTasks, progressed, completed, approved, blocked);
                case "TASK_CREATED" -> new Counts(
                        createdRequirements, createdTasks + 1, progressed, completed, approved, blocked);
                case "TASK_PROGRESSED" -> new Counts(
                        createdRequirements, createdTasks, progressed + 1, completed, approved, blocked);
                case "TASK_COMPLETED" -> new Counts(
                        createdRequirements, createdTasks, progressed, completed + 1, approved, blocked);
                case "TASK_APPROVED" -> new Counts(
                        createdRequirements, createdTasks, progressed, completed, approved + 1, blocked);
                case "TASK_BLOCKED" -> new Counts(
                        createdRequirements, createdTasks, progressed, completed, approved, blocked + 1);
                default -> this;
            };
        }

        private static String normalize(String action) {
            if (action == null) {
                return "";
            }
            return switch (action) {
                case "REQUIREMENT_CREATE" -> "REQUIREMENT_CREATED";
                case "TASK_CREATE" -> "TASK_CREATED";
                case "REQUIREMENT_CONFIRM_ANALYSIS", "TASK_CANCEL" -> "TASK_PROGRESSED";
                case "TASK_APPROVE" -> "TASK_APPROVED";
                case "TASK_ABANDON" -> "TASK_BLOCKED";
                default -> action;
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
