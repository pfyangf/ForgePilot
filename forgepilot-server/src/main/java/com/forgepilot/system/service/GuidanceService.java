package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.ConventionComposition;
import com.forgepilot.system.domain.ConventionCompositionItem;
import com.forgepilot.system.domain.GuidanceItem;
import com.forgepilot.system.guidance.ConventionComposer;
import com.forgepilot.system.guidance.ConventionComposer.ConventionItem;
import com.forgepilot.system.persistence.ConventionCompositionItemMapper;
import com.forgepilot.system.persistence.ConventionCompositionMapper;
import com.forgepilot.system.persistence.GuidanceItemMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GuidanceService {

    private final GuidanceItemMapper guidanceItemMapper;
    private final ConventionCompositionMapper compositionMapper;
    private final ConventionCompositionItemMapper compositionItemMapper;
    private final ProjectAccess projectAccess;
    private final RoleAccess roleAccess;
    private final ActivityLogRecorder activityLogRecorder;

    public GuidanceService(
            GuidanceItemMapper guidanceItemMapper,
            ConventionCompositionMapper compositionMapper,
            ConventionCompositionItemMapper compositionItemMapper,
            ProjectAccess projectAccess,
            RoleAccess roleAccess,
            ActivityLogRecorder activityLogRecorder) {
        this.guidanceItemMapper = guidanceItemMapper;
        this.compositionMapper = compositionMapper;
        this.compositionItemMapper = compositionItemMapper;
        this.projectAccess = projectAccess;
        this.roleAccess = roleAccess;
        this.activityLogRecorder = activityLogRecorder;
    }

    public List<GuidanceView> list(AuthUser user, String scope, String projectId) {
        List<GuidanceItem> items = loadVisible(user, scope, projectId);
        return items.stream().map(this::toView).toList();
    }

    public GuidanceView get(AuthUser user, String id) {
        GuidanceItem item = guidanceItemMapper.selectById(id);
        if (item == null || !canRead(user, item)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规约/文档不存在或无权查看");
        }
        return toView(item);
    }

    @Transactional
    public GuidanceView create(AuthUser user, UpsertRequest request) {
        authorizeWrite(user, request.scope(), request.projectId());
        GuidanceItem item = new GuidanceItem();
        item.setId(UUID.randomUUID().toString());
        applyOwner(item, user, request.scope(), request.projectId());
        applyBody(item, request);
        item.setCreatedBy(user.userId());
        guidanceItemMapper.insert(item);
        activityLogRecorder.record(
                item.getTenantId(),
                "USER",
                user.userId(),
                "GUIDANCE_CREATE",
                "GUIDANCE",
                item.getId(),
                item.getProjectId(),
                null,
                null,
                null,
                "创建" + kindLabel(item.getKind()) + "：" + item.getTitle(),
                Map.of("scope", item.getScope(), "kind", item.getKind(), "title", item.getTitle()),
                "SUCCESS");
        return toView(item);
    }

    @Transactional
    public GuidanceView update(AuthUser user, String id, UpsertRequest request) {
        GuidanceItem item = guidanceItemMapper.selectById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规约/文档不存在");
        }
        authorizeWrite(user, item.getScope(), item.getProjectId());
        if (request.scope() != null && !request.scope().equals(item.getScope())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许改层次");
        }
        applyBody(item, request);
        item.setUpdatedBy(user.userId());
        guidanceItemMapper.updateById(item);
        activityLogRecorder.record(
                item.getTenantId(),
                "USER",
                user.userId(),
                "GUIDANCE_UPDATE",
                "GUIDANCE",
                item.getId(),
                item.getProjectId(),
                null,
                null,
                null,
                "更新" + kindLabel(item.getKind()) + "：" + item.getTitle(),
                Map.of("scope", item.getScope(), "kind", item.getKind(), "title", item.getTitle()),
                "SUCCESS");
        return toView(item);
    }

    public EffectiveConventions composeForProject(AuthUser user, String projectId) {
        Project project = projectAccess.requireProjectMember(user, projectId);
        List<GuidanceItem> items = loadForComposition(project.getTenantId(), projectId);
        List<ConventionItem> composed = ConventionComposer.forAgent(toComposerItems(items));
        return new EffectiveConventions(
                projectId,
                project.getTenantId(),
                composed.stream()
                        .map(c -> new EffectiveItem(c.scope(), c.title(), c.body(), findId(items, c)))
                        .toList());
    }

    @Transactional
    public CompositionSnapshotView snapshotForProject(AuthUser user, String projectId) {
        EffectiveConventions effective = composeForProject(user, projectId);
        String hash = hashComposition(effective.items());
        ConventionComposition header = new ConventionComposition();
        header.setId(UUID.randomUUID().toString());
        header.setTenantId(effective.tenantId());
        header.setProjectId(projectId);
        header.setCompositionHash(hash);
        header.setCreatedBy(user.userId());
        header.setCreatedAt(Instant.now());
        compositionMapper.insert(header);
        for (EffectiveItem item : effective.items()) {
            ConventionCompositionItem row = new ConventionCompositionItem();
            row.setId(UUID.randomUUID().toString());
            row.setCompositionId(header.getId());
            row.setGuidanceItemId(item.guidanceItemId());
            row.setScope(item.scope());
            row.setTitle(item.title());
            row.setBody(item.body());
            row.setContentHash(sha256(item.scope() + "|" + item.title() + "|" + nullToEmpty(item.body())));
            row.setCreatedAt(Instant.now());
            compositionItemMapper.insert(row);
        }
        activityLogRecorder.record(
                effective.tenantId(),
                "USER",
                user.userId(),
                "CONVENTION_SNAPSHOT",
                "CONVENTION_COMPOSITION",
                header.getId(),
                projectId,
                null,
                null,
                null,
                "冻结项目规约合成快照",
                Map.of("compositionHash", hash, "itemCount", effective.items().size()),
                "SUCCESS");
        return new CompositionSnapshotView(header.getId(), projectId, hash, effective.items(), header.getCreatedAt());
    }

    public CompositionSnapshotView getSnapshot(AuthUser user, String snapshotId) {
        ConventionComposition header = compositionMapper.selectById(snapshotId);
        if (header == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "快照不存在");
        }
        projectAccess.requireProjectMember(user, header.getProjectId());
        List<ConventionCompositionItem> rows = compositionItemMapper.selectList(
                new LambdaQueryWrapper<ConventionCompositionItem>()
                        .eq(ConventionCompositionItem::getCompositionId, snapshotId));
        List<EffectiveItem> items = rows.stream()
                .map(r -> new EffectiveItem(r.getScope(), r.getTitle(), r.getBody(), r.getGuidanceItemId()))
                .toList();
        return new CompositionSnapshotView(
                header.getId(), header.getProjectId(), header.getCompositionHash(), items, header.getCreatedAt());
    }

    private List<GuidanceItem> loadVisible(AuthUser user, String scope, String projectId) {
        if (scope == null || scope.isBlank() || "ALL".equalsIgnoreCase(scope)) {
            List<GuidanceItem> all = new ArrayList<>();
            all.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                    .eq(GuidanceItem::getScope, "PLATFORM")
                    .orderByAsc(GuidanceItem::getTitle)));
            if (user.tenantId() != null) {
                all.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                        .eq(GuidanceItem::getScope, "TENANT")
                        .eq(GuidanceItem::getTenantId, user.tenantId())
                        .orderByAsc(GuidanceItem::getTitle)));
                if (projectId != null && !projectId.isBlank()) {
                    projectAccess.requireProjectMember(user, projectId);
                    all.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                            .eq(GuidanceItem::getScope, "PROJECT")
                            .eq(GuidanceItem::getProjectId, projectId)
                            .orderByAsc(GuidanceItem::getTitle)));
                } else {
                    // 所属项目的项目层
                    var memberships = projectAccess.membershipProjectIds(user.userId());
                    if (roleAccess.isTenantAdmin(user)) {
                        all.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                                .eq(GuidanceItem::getScope, "PROJECT")
                                .eq(GuidanceItem::getTenantId, user.tenantId())
                                .orderByAsc(GuidanceItem::getTitle)));
                    } else if (!memberships.isEmpty()) {
                        all.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                                .eq(GuidanceItem::getScope, "PROJECT")
                                .in(GuidanceItem::getProjectId, memberships)
                                .orderByAsc(GuidanceItem::getTitle)));
                    }
                }
            }
            return all;
        }
        return switch (scope.toUpperCase()) {
            case "PLATFORM" -> guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                    .eq(GuidanceItem::getScope, "PLATFORM")
                    .orderByAsc(GuidanceItem::getTitle));
            case "TENANT" -> {
                if (user.tenantId() == null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无租户不能查看公司层");
                }
                yield guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                        .eq(GuidanceItem::getScope, "TENANT")
                        .eq(GuidanceItem::getTenantId, user.tenantId())
                        .orderByAsc(GuidanceItem::getTitle));
            }
            case "PROJECT" -> {
                if (projectId == null || projectId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目层需要 projectId");
                }
                projectAccess.requireProjectMember(user, projectId);
                yield guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                        .eq(GuidanceItem::getScope, "PROJECT")
                        .eq(GuidanceItem::getProjectId, projectId)
                        .orderByAsc(GuidanceItem::getTitle));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知 scope");
        };
    }

    private List<GuidanceItem> loadForComposition(String tenantId, String projectId) {
        List<GuidanceItem> items = new ArrayList<>();
        items.addAll(guidanceItemMapper.selectList(
                new LambdaQueryWrapper<GuidanceItem>().eq(GuidanceItem::getScope, "PLATFORM")));
        items.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                .eq(GuidanceItem::getScope, "TENANT")
                .eq(GuidanceItem::getTenantId, tenantId)));
        items.addAll(guidanceItemMapper.selectList(new LambdaQueryWrapper<GuidanceItem>()
                .eq(GuidanceItem::getScope, "PROJECT")
                .eq(GuidanceItem::getProjectId, projectId)));
        return items;
    }

    private void authorizeWrite(AuthUser user, String scope, String projectId) {
        String normalized = scope == null ? "" : scope.toUpperCase();
        switch (normalized) {
            case "PLATFORM" -> roleAccess.requirePlatformAdmin(user);
            case "TENANT" -> roleAccess.requireTenantAdmin(user);
            case "PROJECT" -> {
                if (projectId == null || projectId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目层需要 projectId");
                }
                projectAccess.requireProjectAdmin(user, projectId);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知 scope");
        }
    }

    private void applyOwner(GuidanceItem item, AuthUser user, String scope, String projectId) {
        String normalized = scope.toUpperCase();
        item.setScope(normalized);
        switch (normalized) {
            case "PLATFORM" -> {
                item.setTenantId(null);
                item.setProjectId(null);
            }
            case "TENANT" -> {
                item.setTenantId(user.tenantId());
                item.setProjectId(null);
            }
            case "PROJECT" -> {
                Project project = projectAccess.requireProjectInTenant(user, projectId);
                item.setTenantId(project.getTenantId());
                item.setProjectId(projectId);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知 scope");
        }
    }

    private void applyBody(GuidanceItem item, UpsertRequest request) {
        String kind = request.kind() == null ? item.getKind() : request.kind().trim().toUpperCase();
        if (!"CONVENTION".equals(kind) && !"DOCUMENT".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind 必须是 CONVENTION 或 DOCUMENT");
        }
        item.setKind(kind);
        item.setTitle(request.title().trim());
        item.setCategory(blankToNull(request.category()));
        item.setBody(blankToNull(request.body()));
        item.setEnabled(request.enabled() == null || request.enabled());
        boolean include = request.includeInAgent() == null
                ? "CONVENTION".equals(kind)
                : request.includeInAgent();
        item.setIncludeInAgent(include);
        item.setContentHash(sha256(item.getScope() + "|" + item.getTitle() + "|" + nullToEmpty(item.getBody())));
    }

    private boolean canRead(AuthUser user, GuidanceItem item) {
        return switch (item.getScope()) {
            case "PLATFORM" -> true;
            case "TENANT" -> user.tenantId() != null && user.tenantId().equals(item.getTenantId());
            case "PROJECT" -> {
                try {
                    projectAccess.requireProjectMember(user, item.getProjectId());
                    yield true;
                } catch (ResponseStatusException ex) {
                    yield false;
                }
            }
            default -> false;
        };
    }

    private List<ConventionItem> toComposerItems(List<GuidanceItem> items) {
        return items.stream()
                .map(i -> new ConventionItem(
                        i.getScope(),
                        i.getTitle(),
                        i.getBody() == null ? "" : i.getBody(),
                        Boolean.TRUE.equals(i.getEnabled()),
                        Boolean.TRUE.equals(i.getIncludeInAgent())))
                .toList();
    }

    private String findId(List<GuidanceItem> items, ConventionItem composed) {
        return items.stream()
                .filter(i -> composed.scope().equals(i.getScope()) && composed.title().equals(i.getTitle()))
                .map(GuidanceItem::getId)
                .findFirst()
                .orElse(null);
    }

    private GuidanceView toView(GuidanceItem item) {
        return new GuidanceView(
                item.getId(),
                item.getScope(),
                item.getTenantId(),
                item.getProjectId(),
                item.getKind(),
                item.getTitle(),
                item.getCategory(),
                item.getBody(),
                Boolean.TRUE.equals(item.getEnabled()),
                Boolean.TRUE.equals(item.getIncludeInAgent()),
                item.getContentHash());
    }

    private static String kindLabel(String kind) {
        return "CONVENTION".equals(kind) ? "规约" : "文档";
    }

    private static String hashComposition(List<EffectiveItem> items) {
        StringBuilder sb = new StringBuilder();
        for (EffectiveItem item : items) {
            sb.append(item.scope()).append('|').append(item.title()).append('|').append(nullToEmpty(item.body())).append('\n');
        }
        return sha256(sb.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record UpsertRequest(
            String scope,
            String projectId,
            String kind,
            String title,
            String category,
            String body,
            Boolean enabled,
            Boolean includeInAgent) {
    }

    public record GuidanceView(
            String id,
            String scope,
            String tenantId,
            String projectId,
            String kind,
            String title,
            String category,
            String body,
            boolean enabled,
            boolean includeInAgent,
            String contentHash) {
    }

    public record EffectiveItem(String scope, String title, String body, String guidanceItemId) {
    }

    public record EffectiveConventions(String projectId, String tenantId, List<EffectiveItem> items) {
    }

    public record CompositionSnapshotView(
            String id, String projectId, String compositionHash, List<EffectiveItem> items, Instant createdAt) {
    }
}
