package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityPayloadMasker;
import com.forgepilot.system.activity.DailyReportBuilder;
import com.forgepilot.system.activity.DailyReportBuilder.ActivityEntry;
import com.forgepilot.system.activity.DailyReportBuilder.WaitingItem;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.ActivityLog;
import com.forgepilot.system.domain.User;
import com.forgepilot.system.persistence.ActivityLogMapper;
import com.forgepilot.system.persistence.UserMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 只读查询。故意不提供 update/delete。 */
@Service
public class ActivityLogQueryService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final ActivityLogMapper activityLogMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final UserMapper userMapper;
    private final ProjectAccess projectAccess;
    private final RoleAccess roleAccess;

    public ActivityLogQueryService(
            ActivityLogMapper activityLogMapper,
            CodingTaskMapper codingTaskMapper,
            UserMapper userMapper,
            ProjectAccess projectAccess,
            RoleAccess roleAccess) {
        this.activityLogMapper = activityLogMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.userMapper = userMapper;
        this.projectAccess = projectAccess;
        this.roleAccess = roleAccess;
    }

    public List<ActivityView> query(
            AuthUser user, String tenantId, String projectId, String actorUserId, String action, Instant from, Instant to) {
        LambdaQueryWrapper<ActivityLog> wrapper = buildWrapper(user, tenantId, projectId, actorUserId, action, from, to);
        wrapper.orderByDesc(ActivityLog::getOccurredAt).last("limit 500");
        List<ActivityLog> logs = activityLogMapper.selectList(wrapper);
        Map<String, User> users = loadActors(logs);
        return logs.stream().map(log -> toView(log, users)).toList();
    }

    public String exportCsv(
            AuthUser user, String tenantId, String projectId, String actorUserId, String action, Instant from, Instant to) {
        List<ActivityView> rows = query(user, tenantId, projectId, actorUserId, action, from, to);
        StringBuilder csv = new StringBuilder();
        csv.append(
                "occurredAt,action,actorUserId,actorUsername,actorDisplayName,tenantId,projectId,targetType,targetId,result,summary\n");
        for (ActivityView row : rows) {
            csv.append(csv(row.occurredAt() == null ? "" : row.occurredAt().toString()))
                    .append(',')
                    .append(csv(row.action()))
                    .append(',')
                    .append(csv(row.actorUserId()))
                    .append(',')
                    .append(csv(row.actorUsername()))
                    .append(',')
                    .append(csv(row.actorDisplayName()))
                    .append(',')
                    .append(csv(row.tenantId()))
                    .append(',')
                    .append(csv(row.projectId()))
                    .append(',')
                    .append(csv(row.targetType()))
                    .append(',')
                    .append(csv(row.targetId()))
                    .append(',')
                    .append(csv(row.result()))
                    .append(',')
                    .append(csv(row.summary()))
                    .append('\n');
        }
        return csv.toString();
    }

    public DailyReportBuilder.DailyReport dailyReport(AuthUser user, LocalDate day, String tenantId) {
        Instant from = day.atStartOfDay(SHANGHAI).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        List<ActivityView> rows = query(user, tenantId, null, null, null, from, to);
        List<ActivityEntry> entries = rows.stream()
                .map(r -> new ActivityEntry(r.actorUserId(), r.projectId(), r.occurredAt(), r.action(), r.result()))
                .toList();
        return DailyReportBuilder.build(entries, day, SHANGHAI, waitingItems(user));
    }

    public String dailyMarkdown(AuthUser user, LocalDate day, String tenantId) {
        return DailyReportBuilder.toMarkdown(dailyReport(user, day, tenantId));
    }

    private Map<String, User> loadActors(List<ActivityLog> logs) {
        Set<String> ids = logs.stream()
                .map(ActivityLog::getActorUserId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ids));
        Map<String, User> byId = new HashMap<>();
        for (User user : users) {
            byId.put(user.getId(), user);
        }
        return byId;
    }

    private List<WaitingItem> waitingItems(AuthUser user) {
        if (user.tenantId() == null && !roleAccess.isPlatformAdmin(user)) {
            return List.of();
        }
        Set<String> projectIds;
        if (roleAccess.isPlatformAdmin(user) && user.tenantId() == null) {
            return List.of();
        }
        if (roleAccess.isTenantAdmin(user)) {
            projectIds = projectAccess.listVisibleProjects(user).stream()
                    .map(p -> p.getId())
                    .collect(Collectors.toSet());
        } else {
            projectIds = projectAccess.membershipProjectIds(user.userId());
        }
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .in(CodingTask::getProjectId, projectIds)
                .in(CodingTask::getStatus, List.of("WAITING_USER", "WAITING_APPROVAL")));
        List<WaitingItem> waiting = new ArrayList<>();
        for (CodingTask task : tasks) {
            waiting.add(new WaitingItem(
                    task.getOwnerId() == null ? task.getCreatedBy() : task.getOwnerId(),
                    task.getProjectId(),
                    task.getId(),
                    task.getStatus()));
        }
        return waiting;
    }

    private LambdaQueryWrapper<ActivityLog> buildWrapper(
            AuthUser user, String tenantId, String projectId, String actorUserId, String action, Instant from, Instant to) {
        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<>();
        if (roleAccess.isPlatformAdmin(user)) {
            if (tenantId != null && !tenantId.isBlank()) {
                wrapper.eq(ActivityLog::getTenantId, tenantId);
            }
        } else {
            if (user.tenantId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查询操作记录");
            }
            if (tenantId != null && !tenantId.isBlank() && !user.tenantId().equals(tenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查询其他公司的操作记录");
            }
            wrapper.eq(ActivityLog::getTenantId, user.tenantId());
            if (!roleAccess.isTenantAdmin(user)) {
                Set<String> memberships = projectAccess.membershipProjectIds(user.userId());
                if (memberships.isEmpty()) {
                    wrapper.and(w -> w.isNull(ActivityLog::getProjectId).eq(ActivityLog::getActorUserId, user.userId()));
                } else {
                    wrapper.and(w -> w.in(ActivityLog::getProjectId, memberships)
                            .or()
                            .isNull(ActivityLog::getProjectId));
                }
            }
        }
        if (projectId != null && !projectId.isBlank()) {
            if (!roleAccess.isPlatformAdmin(user)) {
                projectAccess.requireProjectMember(user, projectId);
            }
            wrapper.eq(ActivityLog::getProjectId, projectId);
        }
        if (actorUserId != null && !actorUserId.isBlank()) {
            wrapper.eq(ActivityLog::getActorUserId, actorUserId);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(ActivityLog::getAction, action);
        }
        if (from != null) {
            wrapper.ge(ActivityLog::getOccurredAt, from);
        }
        if (to != null) {
            wrapper.lt(ActivityLog::getOccurredAt, to);
        }
        return wrapper;
    }

    private ActivityView toView(ActivityLog log, Map<String, User> users) {
        Map<String, Object> payload = ActivityPayloadMasker.mask(log.getPayload());
        User actor = log.getActorUserId() == null ? null : users.get(log.getActorUserId());
        return new ActivityView(
                log.getId(),
                log.getTenantId(),
                log.getActorKind(),
                log.getActorUserId(),
                actor == null ? null : actor.getUsername(),
                actor == null ? null : actor.getDisplayName(),
                log.getOccurredAt(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getProjectId(),
                log.getRequirementId(),
                log.getTaskId(),
                log.getRequestId(),
                log.getSummary(),
                payload,
                log.getResult());
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public record ActivityView(
            String id,
            String tenantId,
            String actorKind,
            String actorUserId,
            String actorUsername,
            String actorDisplayName,
            Instant occurredAt,
            String action,
            String targetType,
            String targetId,
            String projectId,
            String requirementId,
            String taskId,
            String requestId,
            String summary,
            Map<String, Object> payload,
            String result) {
    }
}
