package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.port.ModelProviderPort;
import com.forgepilot.system.port.ModelProviderPort.PlatformModelRequest;
import com.forgepilot.system.web.GenerateDraftRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiDraftService {

    private final ProjectAccess projectAccess;
    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final AnalysisDraftMapper analysisDraftMapper;
    private final RequirementDocumentMapper documentMapper;
    private final ReviewMapper reviewMapper;
    private final DefectMapper defectMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final ModelProviderPort modelProviderPort;

    public AiDraftService(
            ProjectAccess projectAccess,
            ProjectMapper projectMapper,
            RequirementMapper requirementMapper,
            AnalysisDraftMapper analysisDraftMapper,
            RequirementDocumentMapper documentMapper,
            ReviewMapper reviewMapper,
            DefectMapper defectMapper,
            CodingTaskMapper codingTaskMapper,
            ModelProviderPort modelProviderPort) {
        this.projectAccess = projectAccess;
        this.projectMapper = projectMapper;
        this.requirementMapper = requirementMapper;
        this.analysisDraftMapper = analysisDraftMapper;
        this.documentMapper = documentMapper;
        this.reviewMapper = reviewMapper;
        this.defectMapper = defectMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.modelProviderPort = modelProviderPort;
    }

    public DraftView generate(AuthUser user, GenerateDraftRequest request) {
        Context context = resolve(user, request);
        String prompt = buildPrompt(request.scene().trim().toUpperCase(Locale.ROOT), request.hint(), context);
        String text = modelProviderPort.complete(
                new PlatformModelRequest(context.providerId, context.modelId, prompt));
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型没有返回可用文本");
        }
        return new DraftView(request.scene().trim().toUpperCase(Locale.ROOT), text.trim(), context.providerId, context.modelId);
    }

    private Context resolve(AuthUser user, GenerateDraftRequest request) {
        Requirement requirement = null;
        CodingTask task = null;
        Defect defect = null;
        if (request.taskId() != null && !request.taskId().isBlank()) {
            task = codingTaskMapper.selectById(request.taskId());
            if (task == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
            }
            requirement = requirementMapper.selectById(task.getRequirementId());
        } else if (request.requirementId() != null && !request.requirementId().isBlank()) {
            requirement = requirementMapper.selectById(request.requirementId());
        }
        if (request.defectId() != null && !request.defectId().isBlank()) {
            defect = defectMapper.selectById(request.defectId());
            if (defect == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "缺陷不存在");
            }
            if (requirement == null) {
                requirement = requirementMapper.selectById(defect.getRequirementId());
            }
        }
        String projectId = request.projectId();
        if ((projectId == null || projectId.isBlank()) && requirement != null) {
            projectId = requirement.getProjectId();
        }
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择项目或需求，再生成草稿");
        }
        projectAccess.requireProjectMember(user, projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        if (requirement != null && !projectId.equals(requirement.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求不属于该项目");
        }
        String providerId = firstNonBlank(
                request.modelProviderId(),
                task == null ? null : task.getModelProviderId(),
                project.getDefaultModelProviderId());
        String modelId = firstNonBlank(
                request.modelId(), task == null ? null : task.getModelId(), project.getDefaultModelId());
        if (providerId == null || modelId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置模型，无法生成。请先在项目或任务中绑定模型和 Provider。");
        }
        return new Context(project, requirement, task, defect, providerId, modelId);
    }

    private String buildPrompt(String scene, String hint, Context context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ForgePilot 平台的研发助手。请一律使用简体中文（zh-CN）直接给出可编辑的草稿正文，不要使用日文、英文或其他语言，不要解释你在做什么，不要使用 Markdown 代码围栏包裹全文。\n");
        appendContext(prompt, context);
        if (hint != null && !hint.isBlank()) {
            prompt.append("\n用户补充：\n").append(hint.trim()).append("\n");
        }
        prompt.append("\n任务：\n");
        prompt.append(switch (scene) {
            case "REQUIREMENT_ANALYSIS" -> "根据需求正文写出需求分析草稿，包含背景、目标、范围、关键约束和待确认问题。";
            case "REQUIREMENT_REVIEW" -> "根据需求正文和分析稿写出需求评审意见，先给结论（建议通过 / 建议修改），再列要点。";
            case "DESIGN_REWRITE" -> "根据需求、分析稿和已有设计（如有）重写一份页面/交互设计说明，结构清楚、可直接保存为设计文档。";
            case "DESIGN_REVIEW" -> "根据设计文档写出设计评审意见，先给结论，再列与需求或实现风险相关的要点。";
            case "TASK_BRIEF" -> "根据需求、分析、设计和任务标题写出任务说明：目标、实现要点、验收对照。不要编造仓库或分支名。";
            case "CODE_REVIEW" -> "根据任务说明、交付说明和已有证据写出代码审核意见：验证是否通过、变更是否对齐任务，最后给出建议结论（可通过 / 要求修改）。审核通过不会自动合并。";
            case "ACCEPTANCE" -> "根据需求、任务完成情况和缺陷状态写出验收清单草稿，并给出建议结论。未关闭的 OPEN / IN_FIX 缺陷应阻止验收通过。";
            case "TEST_POINTS" -> "根据需求、设计和任务说明列出回归测试要点，每条一行，尽量可执行。";
            case "DEFECT_DESCRIPTION" -> "根据上下文写出缺陷描述：现象、复现步骤、期望结果、严重程度建议。";
            case "RELEASE_NOTES" -> "根据已验收的需求写出发布说明，简要列出变更和注意事项。";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的生成场景：" + scene);
        });
        return prompt.toString();
    }

    private void appendContext(StringBuilder prompt, Context context) {
        prompt.append("项目：").append(context.project.getName()).append("\n");
        if (context.requirement != null) {
            Requirement requirement = context.requirement;
            prompt.append("需求编号：")
                    .append(blankToDash(requirement.getCode()))
                    .append("  标题：")
                    .append(blankToDash(requirement.getTitle()))
                    .append("  状态：")
                    .append(blankToDash(requirement.getStatus()))
                    .append("\n");
            appendBlock(prompt, "需求正文", requirementBody(requirement));
            AnalysisDraft analysis = latestAnalysis(requirement.getId());
            if (analysis != null) {
                appendBlock(prompt, analysis.getConfirmedAt() == null ? "分析稿" : "已确认分析稿", analysis.getBody());
            }
            RequirementDocument document = currentDocument(requirement.getId());
            if (document != null) {
                appendBlock(prompt, "当前设计文档 " + blankToDash(document.getTitle()), document.getBody());
            }
            List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                    .in(Review::getSubjectId, document == null
                            ? List.of(requirement.getId())
                            : List.of(requirement.getId(), document.getId()))
                    .orderByDesc(Review::getCreatedAt));
            if (reviews != null && !reviews.isEmpty()) {
                StringBuilder text = new StringBuilder();
                for (Review review : reviews) {
                    text.append("- ")
                            .append(blankToDash(review.getSubjectType()))
                            .append(" / ")
                            .append(blankToDash(review.getDecision()))
                            .append("：")
                            .append(blankToDash(review.getComment()))
                            .append("\n");
                }
                appendBlock(prompt, "评审结论", text.toString());
            }
        }
        if (context.task != null) {
            CodingTask task = context.task;
            prompt.append("任务编号：")
                    .append(blankToDash(task.getCode()))
                    .append("  标题：")
                    .append(blankToDash(task.getTitle()))
                    .append("  类型：")
                    .append(blankToDash(task.getTaskType()))
                    .append("  状态：")
                    .append(blankToDash(task.getStatus()))
                    .append("\n");
            appendBlock(prompt, "任务说明", task.getDescription());
        }
        if (context.defect != null) {
            Defect defect = context.defect;
            prompt.append("缺陷编号：")
                    .append(blankToDash(defect.getCode()))
                    .append("  标题：")
                    .append(blankToDash(defect.getTitle()))
                    .append("  状态：")
                    .append(blankToDash(defect.getStatus()))
                    .append("\n");
            appendBlock(prompt, "缺陷描述", defect.getDescription());
        }
    }

    private AnalysisDraft latestAnalysis(String requirementId) {
        List<AnalysisDraft> drafts = analysisDraftMapper.selectList(new LambdaQueryWrapper<AnalysisDraft>()
                .eq(AnalysisDraft::getRequirementId, requirementId)
                .isNotNull(AnalysisDraft::getConfirmedAt)
                .orderByDesc(AnalysisDraft::getConfirmedAt));
        if (drafts == null || drafts.isEmpty()) {
            drafts = analysisDraftMapper.selectList(new LambdaQueryWrapper<AnalysisDraft>()
                    .eq(AnalysisDraft::getRequirementId, requirementId));
        }
        return drafts == null || drafts.isEmpty() ? null : drafts.get(0);
    }

    private RequirementDocument currentDocument(String requirementId) {
        List<RequirementDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<RequirementDocument>()
                .eq(RequirementDocument::getRequirementId, requirementId)
                .orderByDesc(RequirementDocument::getUpdatedAt));
        return documents == null || documents.isEmpty() ? null : documents.get(0);
    }

    private static String requirementBody(Requirement requirement) {
        StringBuilder body = new StringBuilder();
        appendNamed(body, "标题", requirement.getTitle());
        appendNamed(body, "说明", requirement.getDescription());
        appendNamed(body, "背景", requirement.getBackground());
        appendNamed(body, "范围", requirement.getScope());
        appendNamed(body, "验收标准", requirement.getAcceptance());
        return body.toString().trim();
    }

    private static void appendNamed(StringBuilder body, String title, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(title).append("：\n").append(value.trim());
    }

    private static void appendBlock(StringBuilder prompt, String title, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        prompt.append(title).append("：\n").append(clip(body, 6000)).append("\n\n");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "\n…（已截断）";
    }

    private static final class Context {
        private final Project project;
        private final Requirement requirement;
        private final CodingTask task;
        private final Defect defect;
        private final String providerId;
        private final String modelId;

        private Context(
                Project project,
                Requirement requirement,
                CodingTask task,
                Defect defect,
                String providerId,
                String modelId) {
            this.project = project;
            this.requirement = requirement;
            this.task = task;
            this.defect = defect;
            this.providerId = providerId;
            this.modelId = modelId;
        }
    }

    public record DraftView(String scene, String text, String modelProviderId, String modelId) {}
}
