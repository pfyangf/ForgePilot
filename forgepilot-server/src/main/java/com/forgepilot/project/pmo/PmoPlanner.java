package com.forgepilot.project.pmo;

import com.forgepilot.project.lifecycle.RequirementStatusMachine;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.pmo.PmoSnapshot.DefectInfo;
import com.forgepilot.project.pmo.PmoSnapshot.TaskInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 确定性的平台 PMO 规划器。根据状态、门禁、任务和缺陷计算下一步建议，不调用模型。
 */
public final class PmoPlanner {

    public static final String KIND_CREATE_TASK = "CREATE_TASK";
    public static final String KIND_BIND_PROJECT = "BIND_PROJECT";
    public static final String KIND_REVIEW_CODE = "REVIEW_CODE";
    public static final String KIND_VERIFY_REGRESSION = "VERIFY_REGRESSION";
    public static final String KIND_CONFIRM_ANALYSIS = "CONFIRM_ANALYSIS";
    public static final String KIND_REVIEW_REQUIREMENT = "REVIEW_REQUIREMENT";
    public static final String KIND_SUBMIT_DESIGN = "SUBMIT_DESIGN";
    public static final String KIND_REVIEW_DESIGN = "REVIEW_DESIGN";
    public static final String KIND_ACCEPT = "ACCEPT";
    public static final String KIND_RELEASE = "RELEASE";
    public static final String KIND_VIEW_RELEASE = "VIEW_RELEASE";

    public static final Set<String> TERMINAL_TASK = Set.of("COMPLETED", "FAILED", "CANCELLED");
    public static final Set<String> DEV_CAPABLE = Set.of("待开发", "开发中", "测试中");
    public static final Set<String> OPEN_DEFECT = Set.of("OPEN", "IN_FIX");
    public static final Set<String> IMPLEMENT_IN_PROGRESS =
            Set.of("PENDING", "PREPARING", "RUNNING", "WAITING_USER", "VERIFYING");

    private static final int TITLE_MAX = 256;

    private PmoPlanner() {}

    public static List<PmoCandidate> plan(PmoSnapshot snapshot) {
        if (snapshot == null || snapshot.status() == null) {
            return List.of();
        }
        List<PmoCandidate> candidates = new ArrayList<>();
        String status = snapshot.status();
        switch (status) {
            case "待分析" -> candidates.add(guidance(
                    snapshot,
                    KIND_CONFIRM_ANALYSIS,
                    "先确认分析草稿和任务建议，再提交需求评审。确认后不会立刻创建任务。",
                    "确认分析并提交评审"));
            case "待需求评审" -> candidates.add(guidance(
                    snapshot,
                    KIND_REVIEW_REQUIREMENT,
                    "以本轮结论为准。通过后进入设计；驳回后回到待分析。",
                    "评审通过"));
            case "设计中" -> candidates.add(guidance(
                    snapshot,
                    KIND_SUBMIT_DESIGN,
                    "写好或导入设计文档后提交评审。已完成节点也可回来修改。",
                    "提交设计评审"));
            case "待设计评审" -> candidates.add(guidance(
                    snapshot,
                    KIND_REVIEW_DESIGN,
                    "通过后进入待开发；驳回后回到设计中。",
                    "评审通过"));
            case "待验收" -> candidates.add(guidance(
                    snapshot,
                    KIND_ACCEPT,
                    "有未关闭的缺陷时不能验收通过。可先生成验收清单，确认后再通过。",
                    "验收通过"));
            case "已完成" -> candidates.add(guidance(
                    snapshot,
                    KIND_RELEASE,
                    "验收通过后才能发布。发布时选择要把工作分支合并进去的目标分支。确认建议不会自动合并。",
                    "确认发布并合并"));
            case "已发布" -> candidates.add(guidance(
                    snapshot, KIND_VIEW_RELEASE, "这条需求已经发布。可回看流水线和发布记录。", "查看发布记录"));
            default -> {
                // 待开发 / 开发中 / 测试中 / 已取消 在下面按规则补充
            }
        }

        if (DEV_CAPABLE.contains(status)) {
            addDevelopmentCandidates(snapshot, candidates);
        }

        for (TaskInfo task : snapshot.tasks()) {
            if ("WAITING_APPROVAL".equals(task.status())) {
                candidates.add(reviewCode(snapshot, task));
            }
        }
        for (DefectInfo defect : snapshot.defects()) {
            if ("FIXED".equals(defect.status())) {
                candidates.add(verifyRegression(snapshot, defect));
            }
        }
        return List.copyOf(candidates);
    }

    /** 未退回且条件仍成立的建议。已确认且任务仍在的 CREATE_TASK 不会再被 plan 出来。 */
    public static List<PmoCandidate> open(List<PmoCandidate> candidates, Set<String> dismissedProposalIds) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (dismissedProposalIds == null || dismissedProposalIds.isEmpty()) {
            return List.copyOf(candidates);
        }
        return candidates.stream()
                .filter(candidate -> !dismissedProposalIds.contains(candidate.proposalId()))
                .toList();
    }

    public static boolean isOpenTask(TaskInfo task) {
        return task != null && task.status() != null && !TERMINAL_TASK.contains(task.status());
    }

    public static String proposalId(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static void addDevelopmentCandidates(PmoSnapshot snapshot, List<PmoCandidate> candidates) {
        GateSnapshot gates = snapshot.gates() == null
                ? new GateSnapshot(false, false, false, false, false, false)
                : snapshot.gates();
        List<String> binding = RequirementStatusMachine.bindingBlockers(gates);
        List<String> review = RequirementStatusMachine.reviewBlockers(gates);
        String status = snapshot.status();

        if (!binding.isEmpty()) {
            candidates.add(bindProject(snapshot, binding));
        }

        boolean startReady = review.isEmpty() && binding.isEmpty();
        if (startReady && ("待开发".equals(status) || "开发中".equals(status)) && !hasOpenType(snapshot, "IMPLEMENT")) {
            candidates.add(createTask(
                    snapshot,
                    "IMPLEMENT",
                    null,
                    prefill("实现：", requirementLabel(snapshot)),
                    "开工门禁已满足。这是平台建议的实现任务，确认后只会创建任务，不会自动启动编码会话。",
                    "确认创建"));
        }

        if (startReady) {
            for (DefectInfo defect : snapshot.defects()) {
                if (!OPEN_DEFECT.contains(defect.status())) {
                    continue;
                }
                if (hasOpenBugfix(snapshot, defect.id())) {
                    continue;
                }
                String label = joinCodeTitle(defect.code(), defect.title());
                candidates.add(createTask(
                        snapshot,
                        "BUGFIX",
                        defect.id(),
                        prefill("修复：", label),
                        "缺陷「" + nullToEmpty(defect.code()) + "」还没有进行中的修复任务。确认后只会创建修复任务，不会自动启动编码会话。",
                        "确认创建"));
            }
        }

        if (shouldProposeTest(snapshot) && gates.repoBound()) {
            candidates.add(createTask(
                    snapshot,
                    "TEST",
                    null,
                    prefill("测试：", requirementLabel(snapshot)),
                    "实现已完成或已进入测试。建议创建测试任务，确认后只会创建任务，不会自动开始执行。",
                    "确认创建"));
        }
    }

    static boolean shouldProposeTest(PmoSnapshot snapshot) {
        if (hasOpenType(snapshot, "TEST")) {
            return false;
        }
        String status = snapshot.status();
        if ("测试中".equals(status)) {
            return true;
        }
        if (!"待开发".equals(status) && !"开发中".equals(status)) {
            return false;
        }
        boolean implementationReady = snapshot.tasks().stream()
                .anyMatch(task -> "IMPLEMENT".equals(task.taskType())
                        && ("COMPLETED".equals(task.status()) || "WAITING_APPROVAL".equals(task.status())));
        boolean implementStillRunning = snapshot.tasks().stream()
                .anyMatch(task -> "IMPLEMENT".equals(task.taskType()) && IMPLEMENT_IN_PROGRESS.contains(task.status()));
        return implementationReady && !implementStillRunning;
    }

    private static boolean hasOpenType(PmoSnapshot snapshot, String taskType) {
        return snapshot.tasks().stream()
                .anyMatch(task -> taskType.equals(task.taskType()) && isOpenTask(task));
    }

    private static boolean hasOpenBugfix(PmoSnapshot snapshot, String defectId) {
        return snapshot.tasks().stream()
                .anyMatch(task -> "BUGFIX".equals(task.taskType())
                        && isOpenTask(task)
                        && defectId != null
                        && defectId.equals(task.defectId()));
    }

    private static PmoCandidate createTask(
            PmoSnapshot snapshot, String taskType, String defectId, String title, String hint, String action) {
        String fingerprint = createTaskFingerprint(snapshot, taskType, defectId);
        return new PmoCandidate(
                KIND_CREATE_TASK,
                taskType,
                defectId,
                null,
                title,
                hint,
                action,
                fingerprint,
                proposalId(fingerprint),
                true);
    }

    private static PmoCandidate bindProject(PmoSnapshot snapshot, List<String> blockers) {
        String fingerprint = String.join("|", KIND_BIND_PROJECT, nullToEmpty(snapshot.status()), String.join(",", blockers));
        return new PmoCandidate(
                KIND_BIND_PROJECT,
                null,
                null,
                null,
                "",
                "开工前还差：" + String.join("；", blockers) + "。请先到项目里绑定编码工具、模型和仓库。",
                "去项目里绑定",
                fingerprint,
                proposalId(fingerprint),
                false);
    }

    private static PmoCandidate reviewCode(PmoSnapshot snapshot, TaskInfo task) {
        String fingerprint = String.join(
                "|", KIND_REVIEW_CODE, nullToEmpty(task.id()), nullToEmpty(task.status()), nullToEmpty(snapshot.status()));
        return new PmoCandidate(
                KIND_REVIEW_CODE,
                task.taskType(),
                task.defectId(),
                task.id(),
                nullToEmpty(task.id()),
                "有任务正在等待代码评审。审核通过不会自动合并。",
                "去评审",
                fingerprint,
                proposalId(fingerprint),
                false);
    }

    private static PmoCandidate verifyRegression(PmoSnapshot snapshot, DefectInfo defect) {
        String fingerprint = String.join(
                "|",
                KIND_VERIFY_REGRESSION,
                nullToEmpty(defect.id()),
                nullToEmpty(defect.status()),
                nullToEmpty(snapshot.status()));
        return new PmoCandidate(
                KIND_VERIFY_REGRESSION,
                null,
                defect.id(),
                null,
                joinCodeTitle(defect.code(), defect.title()),
                "缺陷已修复，请确认回归通过。",
                "回归通过",
                fingerprint,
                proposalId(fingerprint),
                false);
    }

    private static PmoCandidate guidance(PmoSnapshot snapshot, String kind, String hint, String action) {
        String fingerprint = String.join("|", kind, nullToEmpty(snapshot.status()));
        return new PmoCandidate(kind, null, null, null, "", hint, action, fingerprint, proposalId(fingerprint), false);
    }

    static String createTaskFingerprint(PmoSnapshot snapshot, String taskType, String defectId) {
        String relatedIds = snapshot.tasks().stream()
                .filter(task -> taskType.equals(task.taskType()))
                .filter(task -> defectId == null || defectId.equals(task.defectId()))
                .map(TaskInfo::id)
                .filter(id -> id != null && !id.isBlank())
                .sorted()
                .collect(Collectors.joining(","));
        String defectStatus = "";
        if (defectId != null) {
            defectStatus = snapshot.defects().stream()
                    .filter(defect -> defectId.equals(defect.id()))
                    .map(defect -> nullToEmpty(defect.status()))
                    .findFirst()
                    .orElse("");
        }
        return String.join(
                "|",
                KIND_CREATE_TASK,
                nullToEmpty(taskType),
                nullToEmpty(defectId),
                defectStatus,
                nullToEmpty(snapshot.status()),
                relatedIds);
    }

    private static String requirementLabel(PmoSnapshot snapshot) {
        if (notBlank(snapshot.requirementTitle())) {
            return snapshot.requirementTitle().trim();
        }
        return nullToEmpty(snapshot.requirementCode());
    }

    private static String joinCodeTitle(String code, String title) {
        String left = nullToEmpty(code).trim();
        String right = nullToEmpty(title).trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty() || right.startsWith(left)) {
            return left;
        }
        return left + " " + right;
    }

    private static String prefill(String prefix, String body) {
        String text = prefix + nullToEmpty(body).trim();
        if (text.length() <= TITLE_MAX) {
            return text;
        }
        return text.substring(0, TITLE_MAX);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
