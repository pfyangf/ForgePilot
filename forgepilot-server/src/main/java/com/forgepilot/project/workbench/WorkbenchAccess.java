package com.forgepilot.project.workbench;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** 个人工作台可见性：先租户、再项目成员，再「我的 / 待我处理 / 项目内全部」。 */
public final class WorkbenchAccess {

    private WorkbenchAccess() {
    }

    public static boolean sameTenant(String userTenantId, String projectTenantId) {
        if (userTenantId == null || projectTenantId == null) {
            return false;
        }
        return userTenantId.equals(projectTenantId);
    }

    public static boolean isMember(Collection<String> membershipProjectIds, String projectId) {
        return membershipProjectIds != null && membershipProjectIds.contains(projectId);
    }

    public static boolean isMyRequirement(String userId, String createdBy, String ownerId) {
        return Objects.equals(userId, createdBy) || Objects.equals(userId, ownerId);
    }

    public static boolean isMyTask(
            String userId,
            String taskCreatedBy,
            String taskOwnerId,
            String requirementCreatedBy,
            String requirementOwnerId) {
        return Objects.equals(userId, taskCreatedBy)
                || Objects.equals(userId, taskOwnerId)
                || isMyRequirement(userId, requirementCreatedBy, requirementOwnerId);
    }

    public static boolean isWaitingOnMe(String taskStatus, String sessionStatus) {
        return "WAITING_APPROVAL".equals(taskStatus)
                || "WAITING_USER".equals(taskStatus)
                || "WAITING_INPUT".equals(sessionStatus);
    }

    /** 需求处于需要人工跟进的阶段：分析、评审、写设计、待开发或待验收。 */
    public static boolean isRequirementWaitingOnMe(String requirementStatus) {
        return Set.of("待分析", "待需求评审", "设计中", "待设计评审", "待开发", "待验收")
                .contains(requirementStatus);
    }

    public static boolean canSeeProject(String userTenantId, String projectTenantId, Set<String> memberships, String projectId) {
        return sameTenant(userTenantId, projectTenantId) && isMember(memberships, projectId);
    }
}
