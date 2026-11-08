package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.AgentProfile;
import com.forgepilot.execution.persistence.AgentProfileMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.persistence.ModelProviderMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CodingToolCatalogService {

    private final AgentProfileMapper agentProfileMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final RoleAccess roleAccess;
    private final ActivityLogRecorder activityLogRecorder;

    public CodingToolCatalogService(
            AgentProfileMapper agentProfileMapper,
            ModelProviderMapper modelProviderMapper,
            RoleAccess roleAccess,
            ActivityLogRecorder activityLogRecorder) {
        this.agentProfileMapper = agentProfileMapper;
        this.modelProviderMapper = modelProviderMapper;
        this.roleAccess = roleAccess;
        this.activityLogRecorder = activityLogRecorder;
    }

    public List<ToolView> list(AuthUser user) {
        roleAccess.requireTenantAdmin(user);
        return agentProfileMapper
                .selectList(new LambdaQueryWrapper<AgentProfile>()
                        .eq(AgentProfile::getTenantId, user.tenantId())
                        .orderByAsc(AgentProfile::getName))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ToolView get(AuthUser user, String id) {
        return toView(requireOwned(user, id));
    }

    /** 项目成员可只读查看本公司工具目录条目（用于设默认值）。 */
    public List<ToolView> listReadable(AuthUser user) {
        if (user.tenantId() == null) {
            return List.of();
        }
        return agentProfileMapper
                .selectList(new LambdaQueryWrapper<AgentProfile>()
                        .eq(AgentProfile::getTenantId, user.tenantId())
                        .eq(AgentProfile::getEnabled, true)
                        .orderByAsc(AgentProfile::getName))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ToolView create(AuthUser user, UpsertRequest request) {
        roleAccess.requireTenantAdmin(user);
        validateDefaultProvider(user.tenantId(), request.modelProviderId());
        AgentProfile profile = new AgentProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setTenantId(user.tenantId());
        apply(profile, request);
        profile.setCreatedBy(user.userId());
        agentProfileMapper.insert(profile);
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "CODING_TOOL_CREATE",
                "CODING_TOOL",
                profile.getId(),
                null,
                null,
                null,
                null,
                "创建编码工具：" + profile.getName(),
                Map.of("name", profile.getName(), "agentType", profile.getAgentType()),
                "SUCCESS");
        return toView(profile);
    }

    @Transactional
    public ToolView update(AuthUser user, String id, UpsertRequest request) {
        AgentProfile profile = requireOwned(user, id);
        validateDefaultProvider(user.tenantId(), request.modelProviderId());
        apply(profile, request);
        profile.setUpdatedBy(user.userId());
        agentProfileMapper.updateById(profile);
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "CODING_TOOL_UPDATE",
                "CODING_TOOL",
                id,
                null,
                null,
                null,
                null,
                "更新编码工具：" + profile.getName(),
                Map.of("name", profile.getName(), "agentType", profile.getAgentType()),
                "SUCCESS");
        return toView(profile);
    }

    private AgentProfile requireOwned(AuthUser user, String id) {
        roleAccess.requireTenantAdmin(user);
        AgentProfile profile = agentProfileMapper.selectById(id);
        if (profile == null || !user.tenantId().equals(profile.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "编码工具不存在或无权查看");
        }
        return profile;
    }

    private void validateDefaultProvider(String tenantId, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        ModelProvider provider = modelProviderMapper.selectById(providerId);
        if (provider == null || !tenantId.equals(provider.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "默认 Provider 必须属于本公司目录");
        }
    }

    private void apply(AgentProfile profile, UpsertRequest request) {
        profile.setName(request.name().trim());
        profile.setAgentType(request.agentType().trim().toUpperCase());
        profile.setVersion(blankToNull(request.version()));
        profile.setLaunchCommand(blankToNull(request.launchCommand()));
        profile.setModelProviderId(blankToNull(request.modelProviderId()));
        profile.setModelId(blankToNull(request.modelId()));
        profile.setInteractiveInput(Boolean.TRUE.equals(request.interactiveInput()));
        profile.setNativePause(Boolean.TRUE.equals(request.nativePause()));
        profile.setInterrupt(request.interrupt() == null || request.interrupt());
        profile.setEventStream(request.eventStream() == null || request.eventStream());
        profile.setToolEvents(request.toolEvents() == null || request.toolEvents());
        profile.setSessionResume(Boolean.TRUE.equals(request.sessionResume()));
        profile.setStructuredResult(request.structuredResult() == null || request.structuredResult());
        profile.setTimeoutSeconds(request.timeoutSeconds());
        profile.setEnabled(request.enabled() == null || request.enabled());
    }

    private ToolView toView(AgentProfile profile) {
        return new ToolView(
                profile.getId(),
                profile.getTenantId(),
                profile.getName(),
                profile.getAgentType(),
                profile.getVersion(),
                profile.getLaunchCommand(),
                profile.getModelProviderId(),
                profile.getModelId(),
                Boolean.TRUE.equals(profile.getInteractiveInput()),
                Boolean.TRUE.equals(profile.getNativePause()),
                Boolean.TRUE.equals(profile.getInterrupt()),
                Boolean.TRUE.equals(profile.getEventStream()),
                Boolean.TRUE.equals(profile.getToolEvents()),
                Boolean.TRUE.equals(profile.getSessionResume()),
                Boolean.TRUE.equals(profile.getStructuredResult()),
                profile.getTimeoutSeconds(),
                Boolean.TRUE.equals(profile.getEnabled()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record UpsertRequest(
            String name,
            String agentType,
            String version,
            String launchCommand,
            String modelProviderId,
            String modelId,
            Boolean interactiveInput,
            Boolean nativePause,
            Boolean interrupt,
            Boolean eventStream,
            Boolean toolEvents,
            Boolean sessionResume,
            Boolean structuredResult,
            Integer timeoutSeconds,
            Boolean enabled) {
    }

    public record ToolView(
            String id,
            String tenantId,
            String name,
            String agentType,
            String version,
            String launchCommand,
            String modelProviderId,
            String modelId,
            boolean interactiveInput,
            boolean nativePause,
            boolean interrupt,
            boolean eventStream,
            boolean toolEvents,
            boolean sessionResume,
            boolean structuredResult,
            Integer timeoutSeconds,
            boolean enabled) {
    }
}
