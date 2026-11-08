package com.forgepilot.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout.FrozenRepo;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout.PreparedWorkspace;
import com.forgepilot.delivery.service.TaskVerifyDeliveryService;
import com.forgepilot.execution.domain.AgentEvent;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.SessionConventionSnapshot;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.domain.TaskStateHistory;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.persistence.AgentEventMapper;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.SessionConventionSnapshotMapper;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.execution.persistence.TaskStateHistoryMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import com.forgepilot.execution.runtime.AgentRuntimePort;
import com.forgepilot.execution.stream.SessionLogStream;
import com.forgepilot.execution.web.TaskActionRequest;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.model.CodexProviderEligibility;
import com.forgepilot.system.model.ModelSelectionResolver;
import com.forgepilot.system.model.UpstreamWireApi;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import com.forgepilot.system.service.GuidanceService;
import com.forgepilot.system.service.GuidanceService.CompositionSnapshotView;
import com.forgepilot.system.service.GuidanceService.EffectiveItem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Task ≠ Session。本服务在 PostgreSQL 中维护 Session 状态权威，并通过 {@link AgentRuntimePort} 启动执行侧。
 * 不会在 Codex 未真正跑起来时把任务标为 COMPLETED。
 */
@Service
public class TaskSessionService {

    private static final Set<String> ACTIVE_SESSION =
            Set.of("CREATED", "STARTING", "RUNNING", "WAITING_INPUT");

    private final CodingTaskMapper codingTaskMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentEventMapper agentEventMapper;
    private final SessionConventionSnapshotMapper sessionConventionSnapshotMapper;
    private final TaskRepositoryFreezeMapper freezeMapper;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final ProjectMapper projectMapper;
    private final WorkspaceMapper workspaceMapper;
    private final TaskStateHistoryMapper historyMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final IntegrationSecretMapper integrationSecretMapper;
    private final ProjectAccess projectAccess;
    private final GuidanceService guidanceService;
    private final MultiRepoWorkspaceCheckout workspaceCheckout;
    private final AgentRuntimePort agentRuntimePort;
    private final ActivityLogRecorder activityLogRecorder;
    private final NodeRegistryService nodeRegistryService;
    private final SessionLogStream sessionLogStream;
    private final String storageRoot;

    public TaskSessionService(
            CodingTaskMapper codingTaskMapper,
            AgentSessionMapper agentSessionMapper,
            AgentEventMapper agentEventMapper,
            SessionConventionSnapshotMapper sessionConventionSnapshotMapper,
            TaskRepositoryFreezeMapper freezeMapper,
            ProjectRepositoryMapper projectRepositoryMapper,
            ProjectMapper projectMapper,
            WorkspaceMapper workspaceMapper,
            TaskStateHistoryMapper historyMapper,
            ModelProviderMapper modelProviderMapper,
            IntegrationSecretMapper integrationSecretMapper,
            ProjectAccess projectAccess,
            GuidanceService guidanceService,
            MultiRepoWorkspaceCheckout workspaceCheckout,
            AgentRuntimePort agentRuntimePort,
            ActivityLogRecorder activityLogRecorder,
            NodeRegistryService nodeRegistryService,
            SessionLogStream sessionLogStream,
            @Value("${forgepilot.workspace.storage-root:${java.io.tmpdir}/forgepilot-workspaces}") String storageRoot) {
        this.codingTaskMapper = codingTaskMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.agentEventMapper = agentEventMapper;
        this.sessionConventionSnapshotMapper = sessionConventionSnapshotMapper;
        this.freezeMapper = freezeMapper;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.projectMapper = projectMapper;
        this.workspaceMapper = workspaceMapper;
        this.historyMapper = historyMapper;
        this.modelProviderMapper = modelProviderMapper;
        this.integrationSecretMapper = integrationSecretMapper;
        this.projectAccess = projectAccess;
        this.guidanceService = guidanceService;
        this.workspaceCheckout = workspaceCheckout;
        this.agentRuntimePort = agentRuntimePort;
        this.activityLogRecorder = activityLogRecorder;
        this.nodeRegistryService = nodeRegistryService;
        this.sessionLogStream = sessionLogStream;
        this.storageRoot = storageRoot;
    }

    @Transactional
    public SessionActionResult start(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (!List.of("PENDING", "PREPARING", "WAITING_USER").contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不能启动 Session");
        }
        ensureNoActiveSession(taskId);
        return launchNewSession(user, task, request == null ? null : request.note(), "USER_START");
    }

    @Transactional
    public SessionActionResult retry(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (!List.of("WAITING_USER", "RUNNING").contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不能重试");
        }
        cancelActiveSessions(user, task, "重试前取消活动 Session");
        // 同一工作区：不删除 checkout
        return launchNewSession(user, task, request == null ? null : request.note(), "USER_RETRY");
    }

    @Transactional
    public SessionActionResult requestChanges(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (!"WAITING_APPROVAL".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有待审核任务可要求修改");
        }
        ensureNoActiveSession(taskId);
        return launchNewSession(user, task, request == null ? null : request.note(), "REQUEST_CHANGES");
    }

    @Transactional
    public SessionActionResult sendInput(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        AgentSession session = findActiveSession(taskId);
        if (session == null || !"WAITING_INPUT".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有处于 WAITING_INPUT 的 Session");
        }
        String message = request == null ? null : request.note();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息不能为空");
        }
        try {
            agentRuntimePort.sendInput(session.getId(), message);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前适配器不支持向运行中会话发消息，请取消后附说明重试：" + ex.getMessage());
        }
        session.setStatus("RUNNING");
        agentSessionMapper.updateById(session);
        appendEvent(session, "SESSION_INPUT", "用户输入（已掩码长度=" + message.length() + "）");
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "SESSION_INPUT",
                "SESSION",
                session.getId(),
                task.getProjectId(),
                task.getRequirementId(),
                taskId,
                session.getId(),
                "向 WAITING_INPUT Session 发送输入",
                Map.of("messageLength", message.length()),
                "SUCCESS");
        return toResult(task, session, "输入已接受", List.of());
    }

    @Transactional
    public SessionActionResult cancelSession(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        cancelActiveSessions(user, task, request == null ? "用户取消 Session" : request.note());
        String from = task.getStatus();
        if (!List.of("COMPLETED", "FAILED", "CANCELLED").contains(from)) {
            task.setStatus("CANCELLED");
            codingTaskMapper.updateById(task);
            recordHistory(taskId, from, "CANCELLED", "取消任务与 Session", user.userId());
        }
        return toResult(task, findLatestSession(taskId), "已取消", List.of());
    }

    public SessionView getActiveSession(AuthUser user, String taskId) {
        requireTask(user, taskId);
        AgentSession session = findActiveSession(taskId);
        if (session == null) {
            session = findLatestSession(taskId);
        }
        if (session == null) {
            return null;
        }
        List<String> logs = agentRuntimePort.tailLogs(session.getId(), 200);
        List<AgentEventView> events = agentEventMapper
                .selectList(new LambdaQueryWrapper<AgentEvent>()
                        .eq(AgentEvent::getSessionId, session.getId())
                        .orderByAsc(AgentEvent::getSeq))
                .stream()
                .map(e -> new AgentEventView(e.getSeq(), e.getEventType(), e.getSummary(), e.getCreatedAt()))
                .toList();
        return new SessionView(
                session.getId(),
                session.getTaskId(),
                session.getStatus(),
                session.getAgentType(),
                session.getModelProviderId(),
                session.getModelId(),
                session.getWorkspaceId(),
                session.getConventionSetHash(),
                session.getErrorSummary(),
                events,
                logs);
    }

    private SessionActionResult launchNewSession(AuthUser user, CodingTask task, String note, String reason) {
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }

        final ModelSelectionResolver.ModelSelection selection;
        try {
            selection = ModelSelectionResolver.resolve(
                    task.getModelProviderId(),
                    task.getModelId(),
                    project.getDefaultModelProviderId(),
                    project.getDefaultModelId(),
                    null,
                    null);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置模型 Provider，无法启动 Session");
        }
        ModelProvider provider = modelProviderMapper.selectById(selection.providerId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型 Provider 不可用");
        }
        UpstreamWireApi wire = parseWire(provider.getUpstreamWireApi());
        if (!CodexProviderEligibility.eligibleForCodex(true, wire, provider.getTranslationGateway())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "该 Provider 不是 Responses 且未配置翻译网关，不能用于 Codex");
        }

        CompositionSnapshotView snapshot = guidanceService.snapshotForProject(user, project.getId());

        // 准备/复用工作区（retry 不新建 checkout 目录——MultiRepoWorkspaceCheckout 对已存在目录跳过 clone）
        List<FrozenRepo> frozen = loadFrozen(task.getId());
        if (frozen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务没有冻结仓库集合");
        }
        PreparedWorkspace prepared = workspaceCheckout.prepare(storageRoot, task.getId(), frozen, null);
        Workspace workspace = ensureWorkspaceRow(user, task, prepared);

        String fromStatus = task.getStatus();
        task.setStatus("PREPARING");
        codingTaskMapper.updateById(task);
        recordHistory(task.getId(), fromStatus, "PREPARING", "准备 Session：" + reason, user.userId());

        AgentSession session = new AgentSession();
        session.setId(UUID.randomUUID().toString());
        session.setTaskId(task.getId());
        session.setAgentProfileId(task.getAgentProfileId() != null ? task.getAgentProfileId() : project.getDefaultAgentProfileId());
        session.setAgentType("CODEX_CLI");
        session.setStatus("STARTING");
        session.setModelProviderId(provider.getId());
        session.setModelId(selection.modelId());
        var liveNode = nodeRegistryService.pickLiveNode();
        session.setNodeId(liveNode == null ? TaskVerifyDeliveryService.LOCAL_NODE_ID : liveNode.getId());
        session.setWorkspaceId(workspace.getId());
        session.setStartedAt(Instant.now());
        session.setLastEventSeq(0L);
        session.setConventionSetHash(snapshot.compositionHash());
        session.setCreatedBy(user.userId());
        agentSessionMapper.insert(session);

        for (EffectiveItem item : snapshot.items()) {
            SessionConventionSnapshot row = new SessionConventionSnapshot();
            row.setId(UUID.randomUUID().toString());
            row.setSessionId(session.getId());
            row.setGuidanceItemId(item.guidanceItemId());
            row.setScope(item.scope());
            row.setTitle(item.title());
            row.setContentHash(Integer.toHexString((item.scope() + "|" + item.title() + "|" + item.body()).hashCode()));
            row.setCreatedAt(Instant.now());
            sessionConventionSnapshotMapper.insert(row);
        }
        appendEvent(session, "SESSION_CREATED", "创建 Session，规约快照 hash=" + snapshot.compositionHash());

        String upstreamKey = loadProviderKey(provider);
        AgentRuntimePort.StartResult runtime = agentRuntimePort.start(new AgentRuntimePort.StartCommand(
                task.getId(),
                session.getId(),
                prepared.root().toString(),
                prepared.root().resolve(".agent-home").toString(),
                provider.getId(),
                selection.modelId(),
                provider.getBaseUrl(),
                upstreamKey,
                provider.getUpstreamWireApi()));

        // 掩码：活动日志不写密钥
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "SESSION_START",
                "SESSION",
                session.getId(),
                task.getProjectId(),
                task.getRequirementId(),
                task.getId(),
                session.getId(),
                "启动 Agent Session：" + reason,
                Map.of(
                        "reason",
                        reason,
                        "noteLength",
                        note == null ? 0 : note.length(),
                        "providerId",
                        provider.getId(),
                        "modelId",
                        selection.modelId() == null ? "" : selection.modelId(),
                        "wireApi",
                        "responses",
                        "workspaceReused",
                        true,
                        "runtimeErrorCode",
                        runtime.errorCode() == null ? "" : runtime.errorCode()),
                runtime.failed() ? "FAILURE" : "SUCCESS");

        if (runtime.failed()) {
            session.setStatus("FAILED");
            session.setEndedAt(Instant.now());
            session.setErrorSummary(runtime.errorMessage());
            agentSessionMapper.updateById(session);
            appendEvent(session, "SESSION_FAILED", runtime.errorMessage());
            agentRuntimePort.revokeModelToken(session.getId());
            task.setStatus("WAITING_USER");
            codingTaskMapper.updateById(task);
            recordHistory(
                    task.getId(),
                    "PREPARING",
                    "WAITING_USER",
                    "Session 启动失败（" + runtime.errorCode() + "）：" + runtime.errorMessage(),
                    user.userId());
            // 绝不标 COMPLETED / 不假装编码成功
            sessionLogStream.append(session.getId(), runtime.errorMessage());
            return toResult(task, session, runtime.errorMessage(), List.of());
        }

        if (runtime.agentFacingConfig() != null
                && (runtime.agentFacingConfig().containsKey("apiKey")
                        || String.valueOf(runtime.agentFacingConfig()).contains("sk-"))) {
            agentRuntimePort.cancel(session.getId());
            session.setStatus("FAILED");
            session.setErrorSummary("Agent 配置泄露检测：禁止包含 Provider apiKey");
            session.setEndedAt(Instant.now());
            agentSessionMapper.updateById(session);
            task.setStatus("WAITING_USER");
            codingTaskMapper.updateById(task);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 配置安全检查失败");
        }

        session.setStatus("RUNNING");
        agentSessionMapper.updateById(session);
        appendEvent(session, "SESSION_RUNNING", "执行侧已启动；wire_api=responses");
        task.setStatus("RUNNING");
        codingTaskMapper.updateById(task);
        recordHistory(task.getId(), "PREPARING", "RUNNING", "Session 进入 RUNNING", user.userId());
        List<String> logs = agentRuntimePort.tailLogs(session.getId(), 100);
        for (String line : logs) {
            sessionLogStream.append(session.getId(), line);
        }
        return toResult(task, session, "Session 已启动", logs);
    }

    private void cancelActiveSessions(AuthUser user, CodingTask task, String reason) {
        List<AgentSession> active = agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getTaskId, task.getId())
                .in(AgentSession::getStatus, ACTIVE_SESSION));
        for (AgentSession session : active) {
            agentRuntimePort.cancel(session.getId());
            agentRuntimePort.revokeModelToken(session.getId());
            session.setStatus("CANCELLED");
            session.setEndedAt(Instant.now());
            session.setErrorSummary(reason);
            agentSessionMapper.updateById(session);
            appendEvent(session, "SESSION_CANCELLED", reason == null ? "cancelled" : reason);
            activityLogRecorder.record(
                    user.tenantId(),
                    "USER",
                    user.userId(),
                    "SESSION_CANCEL",
                    "SESSION",
                    session.getId(),
                    task.getProjectId(),
                    task.getRequirementId(),
                    task.getId(),
                    session.getId(),
                    "取消 Session",
                    Map.of("reason", reason == null ? "" : reason),
                    "SUCCESS");
        }
    }

    private Workspace ensureWorkspaceRow(AuthUser user, CodingTask task, PreparedWorkspace prepared) {
        Workspace existing = workspaceMapper.selectOne(new LambdaQueryWrapper<Workspace>().eq(Workspace::getTaskId, task.getId()));
        if (existing != null) {
            return existing;
        }
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID().toString());
        ws.setTaskId(task.getId());
        ws.setNodeId(TaskVerifyDeliveryService.LOCAL_NODE_ID);
        ws.setRootPath(prepared.root().toString());
        ws.setAgentHomePath(prepared.root().resolve(".agent-home").toString());
        ws.setStatus("READY");
        ws.setCreatedBy(user.userId());
        workspaceMapper.insert(ws);
        return ws;
    }

    private List<FrozenRepo> loadFrozen(String taskId) {
        List<TaskRepositoryFreeze> freezes = freezeMapper.selectList(new LambdaQueryWrapper<TaskRepositoryFreeze>()
                .eq(TaskRepositoryFreeze::getTaskId, taskId));
        List<FrozenRepo> result = new ArrayList<>();
        for (TaskRepositoryFreeze freeze : freezes) {
            ProjectRepository repo = projectRepositoryMapper.selectById(freeze.getProjectRepositoryId());
            if (repo == null) {
                continue;
            }
            result.add(new FrozenRepo(
                    repo.getId(),
                    repo.getLocalDirectoryName(),
                    repo.getCloneUrl(),
                    freeze.getBaseBranch(),
                    freeze.getBaseSha(),
                    repo.getProviderType(),
                    repo.getExternalId()));
        }
        return result;
    }

    private String loadProviderKey(ModelProvider provider) {
        if (provider.getSecretId() == null || provider.getSecretId().isBlank()) {
            return "";
        }
        IntegrationSecret secret = integrationSecretMapper.selectById(provider.getSecretId());
        if (secret == null || secret.getCiphertext() == null) {
            return "";
        }
        return new String(secret.getCiphertext(), StandardCharsets.UTF_8);
    }

    private void ensureNoActiveSession(String taskId) {
        Long count = agentSessionMapper.selectCount(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getTaskId, taskId)
                .in(AgentSession::getStatus, ACTIVE_SESSION));
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务已有活动 Session");
        }
    }

    private AgentSession findActiveSession(String taskId) {
        return agentSessionMapper.selectOne(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getTaskId, taskId)
                .in(AgentSession::getStatus, ACTIVE_SESSION)
                .last("limit 1"));
    }

    private AgentSession findLatestSession(String taskId) {
        return agentSessionMapper.selectOne(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getTaskId, taskId)
                .orderByDesc(AgentSession::getStartedAt)
                .last("limit 1"));
    }

    private void appendEvent(AgentSession session, String type, String summary) {
        long seq = session.getLastEventSeq() == null ? 1L : session.getLastEventSeq() + 1;
        session.setLastEventSeq(seq);
        agentSessionMapper.updateById(session);
        AgentEvent event = new AgentEvent();
        event.setId(UUID.randomUUID().toString());
        event.setSessionId(session.getId());
        event.setTaskId(session.getTaskId());
        event.setSeq(seq);
        event.setEventType(type);
        event.setSummary(summary);
        event.setCreatedAt(Instant.now());
        agentEventMapper.insert(event);
    }

    private CodingTask requireTask(AuthUser user, String taskId) {
        CodingTask task = codingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, task.getProjectId());
        return task;
    }

    private void recordHistory(String taskId, String from, String to, String reason, String actorId) {
        TaskStateHistory history = new TaskStateHistory();
        history.setId(UUID.randomUUID().toString());
        history.setTaskId(taskId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setActorId(actorId);
        history.setCreatedAt(Instant.now());
        historyMapper.insert(history);
    }

    private SessionActionResult toResult(CodingTask task, AgentSession session, String message, List<String> logs) {
        return new SessionActionResult(
                task.getId(),
                task.getStatus(),
                session == null ? null : session.getId(),
                session == null ? null : session.getStatus(),
                message,
                logs == null ? List.of() : logs);
    }

    private static UpstreamWireApi parseWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return UpstreamWireApi.RESPONSES;
        }
        try {
            return UpstreamWireApi.parse(raw.trim().toLowerCase());
        } catch (Exception ex) {
            try {
                return UpstreamWireApi.valueOf(raw.trim().toUpperCase());
            } catch (Exception ignored) {
                return UpstreamWireApi.CHAT_COMPLETIONS;
            }
        }
    }

    public record AgentEventView(Long seq, String eventType, String summary, Instant createdAt) {
    }

    public record SessionView(
            String id,
            String taskId,
            String status,
            String agentType,
            String modelProviderId,
            String modelId,
            String workspaceId,
            String conventionSetHash,
            String errorSummary,
            List<AgentEventView> events,
            List<String> logs) {
    }

    public record SessionActionResult(
            String taskId,
            String taskStatus,
            String sessionId,
            String sessionStatus,
            String message,
            List<String> logs) {
    }
}
