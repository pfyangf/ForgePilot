package com.forgepilot.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.SessionConventionSnapshot;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.SessionConventionSnapshotMapper;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.service.GuidanceService;
import com.forgepilot.system.service.GuidanceService.EffectiveItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 启动 Agent Session 时组装并冻结任务相关文档，而不只给任务说明。
 * 一般文档仍按「进入 Agent」标记追加；会话开始后再改源文档不会影响已冻结的包。
 */
@Service
public class TaskDocumentPackService {

    public static final String TASK_BRIEF = "TASK_BRIEF";
    public static final String REQUIREMENT_BODY = "REQUIREMENT_BODY";
    public static final String ANALYSIS = "ANALYSIS";
    public static final String DESIGN_DOC = "DESIGN_DOC";
    public static final String REVIEW_CONCLUSIONS = "REVIEW_CONCLUSIONS";
    public static final String CONVENTION = "CONVENTION";
    public static final String GUIDANCE = "GUIDANCE";
    public static final String DEFECT = "DEFECT";

    private final CodingTaskMapper codingTaskMapper;
    private final RequirementMapper requirementMapper;
    private final AnalysisDraftMapper analysisDraftMapper;
    private final RequirementDocumentMapper documentMapper;
    private final ReviewMapper reviewMapper;
    private final DefectMapper defectMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final SessionConventionSnapshotMapper snapshotMapper;
    private final GuidanceService guidanceService;
    private final ProjectAccess projectAccess;

    public TaskDocumentPackService(
            CodingTaskMapper codingTaskMapper,
            RequirementMapper requirementMapper,
            AnalysisDraftMapper analysisDraftMapper,
            RequirementDocumentMapper documentMapper,
            ReviewMapper reviewMapper,
            DefectMapper defectMapper,
            AgentSessionMapper agentSessionMapper,
            SessionConventionSnapshotMapper snapshotMapper,
            GuidanceService guidanceService,
            ProjectAccess projectAccess) {
        this.codingTaskMapper = codingTaskMapper;
        this.requirementMapper = requirementMapper;
        this.analysisDraftMapper = analysisDraftMapper;
        this.documentMapper = documentMapper;
        this.reviewMapper = reviewMapper;
        this.defectMapper = defectMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.snapshotMapper = snapshotMapper;
        this.guidanceService = guidanceService;
        this.projectAccess = projectAccess;
    }

    public DocumentPackView preview(AuthUser user, String taskId) {
        CodingTask task = requireTask(user, taskId);
        return toView(assemble(user, task), false, null);
    }

    public DocumentPackView frozenOrPreview(AuthUser user, String taskId, String sessionId) {
        CodingTask task = requireTask(user, taskId);
        AgentSession session = resolveSession(taskId, sessionId);
        if (session != null) {
            DocumentPack pack = loadFrozen(session.getId());
            if (!pack.items().isEmpty()) {
                return toView(pack, true, session.getId());
            }
        }
        return toView(assemble(user, task), false, null);
    }

    public DocumentPack assemble(AuthUser user, CodingTask task) {
        projectAccess.requireProjectMember(user, task.getProjectId());
        Requirement requirement = requirementMapper.selectById(task.getRequirementId());
        List<PackItem> items = new ArrayList<>();
        int order = 0;
        items.add(item(
                TASK_BRIEF,
                "任务说明",
                nullToEmpty(task.getCode()) + " 本页编码说明",
                nullToEmpty(task.getCode()),
                nullToEmpty(task.getDescription()),
                ++order,
                null));
        items.add(item(
                REQUIREMENT_BODY,
                "需求正文",
                requirement == null
                        ? "需求正文"
                        : nullToEmpty(requirement.getCode()) + " " + nullToEmpty(requirement.getTitle()),
                requirement == null ? "" : requirement.getCode(),
                requirement == null ? "" : requirementBody(requirement),
                ++order,
                null));
        AnalysisDraft analysis = confirmedAnalysis(task.getRequirementId());
        items.add(item(
                ANALYSIS,
                "需求分析",
                analysis == null ? "分析稿（尚未确认）" : "已确认分析稿",
                analysis == null ? "" : analysis.getId(),
                analysis == null ? "" : nullToEmpty(analysis.getBody()),
                ++order,
                null));
        RequirementDocument design = currentDesign(task.getRequirementId());
        String designTitle = design == null
                ? "设计文档"
                : nullToEmpty(design.getTitle())
                        + (design.getVersionNo() == null ? "" : " v" + design.getVersionNo());
        items.add(item(
                DESIGN_DOC,
                "设计文档",
                designTitle,
                design == null ? "" : design.getId(),
                design == null ? "" : nullToEmpty(design.getBody()),
                ++order,
                null));
        items.add(item(
                REVIEW_CONCLUSIONS,
                "评审结论",
                "需求评审、设计评审通过意见",
                task.getRequirementId(),
                reviewConclusions(task.getRequirementId()),
                ++order,
                null));
        List<EffectiveItem> conventions = List.of();
        try {
            conventions = guidanceService.composeForProject(user, task.getProjectId()).items();
        } catch (RuntimeException ignored) {
            // 项目规约不可用时仍返回任务相关文档
        }
        StringBuilder composed = new StringBuilder();
        for (EffectiveItem row : conventions) {
            if (!composed.isEmpty()) {
                composed.append("\n\n");
            }
            composed.append("[").append(row.scope()).append("] ").append(row.title()).append("\n");
            composed.append(nullToEmpty(row.body()).trim());
        }
        items.add(item(
                CONVENTION,
                "合成规约",
                "平台 / 公司 / 项目三层合成",
                "",
                composed.toString(),
                ++order,
                null));
        for (EffectiveItem row : conventions) {
            items.add(item(
                    GUIDANCE,
                    scopeLabel(row.scope()),
                    row.title(),
                    row.guidanceItemId(),
                    nullToEmpty(row.body()),
                    ++order,
                    row.guidanceItemId()));
        }
        if ("BUGFIX".equals(task.getTaskType()) && task.getDefectId() != null && !task.getDefectId().isBlank()) {
            Defect defect = defectMapper.selectById(task.getDefectId());
            items.add(item(
                    DEFECT,
                    "关联缺陷",
                    defect == null
                            ? "关联缺陷"
                            : nullToEmpty(defect.getCode()) + " " + nullToEmpty(defect.getTitle()),
                    task.getDefectId(),
                    defect == null ? "" : defectBody(defect),
                    ++order,
                    null));
        }
        return new DocumentPack(List.copyOf(items));
    }

    public void persist(String sessionId, DocumentPack pack) {
        int order = 0;
        for (PackItem item : pack.items()) {
            SessionConventionSnapshot row = new SessionConventionSnapshot();
            row.setId(UUID.randomUUID().toString());
            row.setSessionId(sessionId);
            row.setGuidanceItemId(blankToNull(item.guidanceItemId()));
            row.setScope(scopeOf(item.kind()));
            row.setTitle(item.title());
            row.setContentHash(sha256(item.kind() + "|" + item.title() + "|" + item.body()));
            row.setKind(item.kind());
            row.setSourceRef(blankToNull(item.sourceRef()));
            row.setBody(item.body());
            row.setIncluded(item.included());
            row.setSortOrder(++order);
            row.setCreatedAt(Instant.now());
            snapshotMapper.insert(row);
        }
    }

    public DocumentPack loadFrozen(String sessionId) {
        List<SessionConventionSnapshot> rows = snapshotMapper.selectList(
                new LambdaQueryWrapper<SessionConventionSnapshot>()
                        .eq(SessionConventionSnapshot::getSessionId, sessionId)
                        .orderByAsc(SessionConventionSnapshot::getSortOrder)
                        .orderByAsc(SessionConventionSnapshot::getCreatedAt));
        List<PackItem> items = new ArrayList<>();
        for (SessionConventionSnapshot row : rows) {
            String kind = row.getKind() == null || row.getKind().isBlank() ? CONVENTION : row.getKind();
            items.add(new PackItem(
                    kind,
                    typeLabel(kind),
                    row.getTitle(),
                    nullToEmpty(row.getSourceRef()),
                    row.getBody() == null ? "" : row.getBody(),
                    row.getIncluded() == null || Boolean.TRUE.equals(row.getIncluded()),
                    row.getSortOrder() == null ? items.size() + 1 : row.getSortOrder(),
                    row.getGuidanceItemId()));
        }
        items.sort(Comparator.comparingInt(PackItem::sortOrder));
        return new DocumentPack(List.copyOf(items));
    }

    public String toPrompt(DocumentPack pack, String branchName, String note) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ForgePilot 的编码执行者。请在当前工作区完成下面的任务，不要另起分支名（平台已使用 ")
                .append(branchName == null || branchName.isBlank() ? "指定工作分支" : branchName)
                .append("）。\n");
        prompt.append("启动时平台已把下列任务相关文档一并交给你，而不只是任务说明。请综合使用这些材料。\n\n");
        for (PackItem item : pack.items()) {
            if (!item.included()) {
                continue;
            }
            prompt.append("## ").append(item.typeLabel()).append(" · ").append(item.title()).append("\n");
            if (item.body() == null || item.body().isBlank()) {
                prompt.append("（当时没有可写入的正文）\n\n");
            } else {
                prompt.append(item.body().trim()).append("\n\n");
            }
        }
        if (note != null && !note.isBlank()) {
            prompt.append("## 附加说明\n").append(note.trim()).append("\n");
        }
        return prompt.toString().trim();
    }

    public DocumentPackView toView(DocumentPack pack, boolean frozen, String sessionId) {
        return new DocumentPackView(
                frozen,
                sessionId,
                pack.items().stream()
                        .map(item -> new DocumentPackItemView(
                                item.kind(),
                                item.typeLabel(),
                                item.title(),
                                item.sourceRef(),
                                item.body(),
                                item.included(),
                                item.sortOrder()))
                        .toList());
    }

    private CodingTask requireTask(AuthUser user, String taskId) {
        CodingTask task = codingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        projectAccess.requireProjectMember(user, task.getProjectId());
        return task;
    }

    private AgentSession resolveSession(String taskId, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            AgentSession session = agentSessionMapper.selectById(sessionId);
            if (session != null && taskId.equals(session.getTaskId())) {
                return session;
            }
            return null;
        }
        List<AgentSession> sessions = agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getTaskId, taskId)
                .orderByDesc(AgentSession::getStartedAt));
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private AnalysisDraft confirmedAnalysis(String requirementId) {
        return analysisDraftMapper.selectOne(new LambdaQueryWrapper<AnalysisDraft>()
                .eq(AnalysisDraft::getRequirementId, requirementId)
                .isNotNull(AnalysisDraft::getConfirmedAt)
                .orderByDesc(AnalysisDraft::getConfirmedAt)
                .last("limit 1"));
    }

    private RequirementDocument currentDesign(String requirementId) {
        return documentMapper.selectOne(new LambdaQueryWrapper<RequirementDocument>()
                .eq(RequirementDocument::getRequirementId, requirementId)
                .eq(RequirementDocument::getKind, "DESIGN")
                .eq(RequirementDocument::getCurrentFlag, true)
                .last("limit 1"));
    }

    private String reviewConclusions(String requirementId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getSubjectId, requirementId)
                .in(Review::getSubjectType, List.of("REQUIREMENT", "DESIGN_DOCUMENT"))
                .orderByDesc(Review::getCreatedAt));
        // 设计评审的 subjectId 可能是文档 id，再按需求下的设计文档补齐
        List<RequirementDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<RequirementDocument>()
                .eq(RequirementDocument::getRequirementId, requirementId));
        if (!docs.isEmpty()) {
            List<String> docIds = docs.stream().map(RequirementDocument::getId).toList();
            List<Review> designReviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                    .eq(Review::getSubjectType, "DESIGN_DOCUMENT")
                    .in(Review::getSubjectId, docIds)
                    .orderByDesc(Review::getCreatedAt));
            reviews = new ArrayList<>(reviews);
            for (Review review : designReviews) {
                boolean exists = reviews.stream().anyMatch(item -> item.getId().equals(review.getId()));
                if (!exists) {
                    reviews.add(review);
                }
            }
            reviews.sort(Comparator.comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed());
        }
        Review requirementLatest = latest(reviews, "REQUIREMENT");
        Review designLatest = latest(reviews, "DESIGN_DOCUMENT");
        StringBuilder body = new StringBuilder();
        appendReview(body, "需求评审", requirementLatest);
        appendReview(body, "设计评审", designLatest);
        return body.toString().trim();
    }

    private static Review latest(List<Review> reviews, String subjectType) {
        return reviews.stream()
                .filter(item -> subjectType.equals(item.getSubjectType()))
                .findFirst()
                .orElse(null);
    }

    private static void appendReview(StringBuilder body, String label, Review review) {
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(label).append("：");
        if (review == null) {
            body.append("暂无记录");
            return;
        }
        body.append(decisionLabel(review.getDecision()));
        if (review.getComment() != null && !review.getComment().isBlank()) {
            body.append("\n").append(review.getComment().trim());
        }
    }

    private static String decisionLabel(String decision) {
        if ("APPROVED".equals(decision)) {
            return "通过";
        }
        if ("REJECTED".equals(decision)) {
            return "驳回";
        }
        return decision == null ? "未知" : decision;
    }

    private static String requirementBody(Requirement requirement) {
        StringBuilder body = new StringBuilder();
        appendSection(body, "标题", requirement.getTitle());
        appendSection(body, "说明", requirement.getDescription());
        appendSection(body, "背景", requirement.getBackground());
        appendSection(body, "范围", requirement.getScope());
        appendSection(body, "验收标准", requirement.getAcceptance());
        return body.toString().trim();
    }

    private static String defectBody(Defect defect) {
        StringBuilder body = new StringBuilder();
        appendSection(body, "编号", defect.getCode());
        appendSection(body, "标题", defect.getTitle());
        appendSection(body, "严重程度", defect.getSeverity());
        appendSection(body, "状态", defect.getStatus());
        appendSection(body, "描述", defect.getDescription());
        return body.toString().trim();
    }

    private static void appendSection(StringBuilder body, String title, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(title).append("：\n").append(value.trim());
    }

    private static PackItem item(
            String kind, String typeLabel, String title, String sourceRef, String body, int order, String guidanceId) {
        return new PackItem(kind, typeLabel, title, nullToEmpty(sourceRef), nullToEmpty(body), true, order, guidanceId);
    }

    private static String scopeOf(String kind) {
        if (CONVENTION.equals(kind) || GUIDANCE.equals(kind)) {
            return "COMPOSED";
        }
        return "TASK";
    }

    private static String typeLabel(String kind) {
        return switch (kind) {
            case TASK_BRIEF -> "任务说明";
            case REQUIREMENT_BODY -> "需求正文";
            case ANALYSIS -> "需求分析";
            case DESIGN_DOC -> "设计文档";
            case REVIEW_CONCLUSIONS -> "评审结论";
            case CONVENTION -> "合成规约";
            case GUIDANCE -> "一般文档";
            case DEFECT -> "关联缺陷";
            default -> kind;
        };
    }

    private static String scopeLabel(String scope) {
        if ("PLATFORM".equals(scope)) {
            return "平台规约";
        }
        if ("TENANT".equals(scope)) {
            return "公司规约";
        }
        if ("PROJECT".equals(scope)) {
            return "项目规约";
        }
        return "一般文档";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record PackItem(
            String kind,
            String typeLabel,
            String title,
            String sourceRef,
            String body,
            boolean included,
            int sortOrder,
            String guidanceItemId) {}

    public record DocumentPack(List<PackItem> items) {}

    public record DocumentPackItemView(
            String kind,
            String typeLabel,
            String title,
            String sourceRef,
            String body,
            boolean included,
            int sortOrder) {}

    public record DocumentPackView(boolean frozen, String sessionId, List<DocumentPackItemView> items) {}
}
