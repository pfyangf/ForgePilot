package com.forgepilot.project.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 需求研发生命周期的纯状态规则。持久化和鉴权在 {@link RequirementLifecycleService}。
 */
public final class RequirementStatusMachine {

    public static final List<String> STATUSES = List.of(
            "待分析",
            "待需求评审",
            "设计中",
            "待设计评审",
            "待开发",
            "开发中",
            "测试中",
            "待验收",
            "已完成",
            "已取消",
            "已发布");

    public static final List<String> STAGES = List.of(
            "提出需求",
            "需求分析",
            "需求评审",
            "设计文档",
            "设计文档评审",
            "AI Coding 绑定",
            "仓库绑定",
            "开发",
            "代码评审",
            "测试",
            "缺陷登记",
            "缺陷修改",
            "回归测试",
            "验收测试",
            "发布");

    public static final Set<String> AUTO_LOCKED = Set.of("已完成", "已取消", "已发布");

    public static final Set<String> EXECUTING_TASK =
            Set.of("PREPARING", "RUNNING", "WAITING_USER", "VERIFYING", "WAITING_APPROVAL");

    public static final Set<String> GATED_TASK_TYPES = Set.of("IMPLEMENT", "BUGFIX");

    public static final Set<String> DEV_TASK_TYPES = Set.of("IMPLEMENT", "BUGFIX", "REFACTOR");

    public static final Set<String> BLOCKING_DEFECT_STATUSES = Set.of("OPEN", "IN_FIX");

    private RequirementStatusMachine() {}

    public static boolean isAutoLocked(String status) {
        return AUTO_LOCKED.contains(status);
    }

    public static String afterConfirmAnalysis() {
        return "待需求评审";
    }

    public static String afterRequirementReview(String decision) {
        requireDecision(decision);
        return "APPROVED".equals(decision) ? "设计中" : "待分析";
    }

    public static String afterSubmitDesignReview() {
        return "待设计评审";
    }

    public static String afterDesignReview(String decision) {
        requireDecision(decision);
        return "APPROVED".equals(decision) ? "待开发" : "设计中";
    }

    public static String afterAcceptance(boolean accepted, boolean hasBlockingDefects) {
        if (accepted) {
            return "已完成";
        }
        return hasBlockingDefects ? "开发中" : "测试中";
    }

    public static boolean canManuallySet(String status) {
        return STATUSES.contains(status) && !"已发布".equals(status);
    }

    public static boolean canAccept(String status, boolean hasBlockingDefects) {
        return "待验收".equals(status) && !hasBlockingDefects;
    }

    public static boolean canRelease(String status) {
        return "已完成".equals(status);
    }

    public static boolean requiresStartGate(String taskType) {
        return GATED_TASK_TYPES.contains(taskType);
    }

    public static String autoStatus(String current, List<TaskSnapshot> tasks, boolean hasBlockingDefects) {
        if (current == null || isAutoLocked(current)) {
            return current;
        }
        List<TaskSnapshot> active = tasks.stream().filter(t -> !"CANCELLED".equals(t.status())).toList();
        boolean anyDevExecuting = active.stream()
                .anyMatch(t -> DEV_TASK_TYPES.contains(t.taskType()) && EXECUTING_TASK.contains(t.status()));
        boolean anyTestExecuting =
                active.stream().anyMatch(t -> "TEST".equals(t.taskType()) && EXECUTING_TASK.contains(t.status()));
        boolean allCompleted =
                !active.isEmpty() && active.stream().allMatch(t -> "COMPLETED".equals(t.status()));

        if (anyDevExecuting) {
            return "开发中";
        }
        if (anyTestExecuting) {
            return "测试中";
        }
        if (allCompleted && !hasBlockingDefects) {
            return "待验收";
        }
        if (allCompleted && hasBlockingDefects) {
            return "开发中";
        }
        return current;
    }

    public static List<String> reviewBlockers(GateSnapshot gates) {
        List<String> blockers = new ArrayList<>();
        if (!gates.requirementReviewApproved()) {
            blockers.add("最近一次需求评审尚未通过");
        }
        if (!gates.designDocumentExists()) {
            blockers.add("尚未编写设计文档");
        }
        if (!gates.designReviewApproved()) {
            blockers.add("最近一次设计文档评审尚未通过");
        }
        return List.copyOf(blockers);
    }

    public static List<String> bindingBlockers(GateSnapshot gates) {
        List<String> blockers = new ArrayList<>();
        if (!gates.codingToolBound()) {
            blockers.add("项目未绑定默认编码工具");
        }
        if (!gates.modelBound()) {
            blockers.add("项目未绑定默认模型");
        }
        if (!gates.repoBound()) {
            blockers.add("项目尚未关联仓库");
        }
        return List.copyOf(blockers);
    }

    public static List<String> startBlockers(String taskType, GateSnapshot gates) {
        if (!requiresStartGate(taskType)) {
            return List.of();
        }
        List<String> blockers = new ArrayList<>();
        blockers.addAll(reviewBlockers(gates));
        blockers.addAll(bindingBlockers(gates));
        return List.copyOf(blockers);
    }

    public static int currentStageIndex(
            String status, GateSnapshot gates, List<TaskSnapshot> tasks, List<DefectSnapshot> defects) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case "待分析" -> 1;
            case "待需求评审" -> 2;
            case "设计中" -> 3;
            case "待设计评审" -> 4;
            case "待开发" -> {
                if (gates == null || !gates.codingToolBound() || !gates.modelBound()) {
                    yield 5;
                }
                if (!gates.repoBound()) {
                    yield 6;
                }
                yield 7;
            }
            case "开发中" -> {
                boolean waitingApproval = tasks.stream().anyMatch(t -> "WAITING_APPROVAL".equals(t.status()));
                yield waitingApproval ? 8 : 7;
            }
            case "测试中" -> {
                boolean fixing = defects.stream()
                        .anyMatch(d -> "OPEN".equals(d.status()) || "IN_FIX".equals(d.status()));
                boolean regression = defects.stream().anyMatch(d -> "FIXED".equals(d.status()));
                if (fixing) {
                    yield 11;
                }
                if (regression) {
                    yield 12;
                }
                yield 9;
            }
            case "待验收" -> 13;
            case "已完成" -> 14;
            case "已发布" -> 14;
            default -> 0;
        };
    }

    private static void requireDecision(String decision) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("评审结论必须是 APPROVED 或 REJECTED");
        }
    }

    public record TaskSnapshot(String taskType, String status) {}

    public record DefectSnapshot(String status) {}

    public record GateSnapshot(
            boolean requirementReviewApproved,
            boolean designDocumentExists,
            boolean designReviewApproved,
            boolean codingToolBound,
            boolean modelBound,
            boolean repoBound) {
        public boolean canStartImplementOrBugfix() {
            return requirementReviewApproved
                    && designDocumentExists
                    && designReviewApproved
                    && codingToolBound
                    && modelBound
                    && repoBound;
        }
    }
}
