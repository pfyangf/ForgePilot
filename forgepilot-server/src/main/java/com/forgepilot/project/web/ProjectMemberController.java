package com.forgepilot.project.web;

import com.forgepilot.project.service.MemberAdminService;
import com.forgepilot.project.service.MemberAdminService.InviteMemberRequest;
import com.forgepilot.project.service.MemberAdminService.ProjectMemberView;
import com.forgepilot.system.security.AuthContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final MemberAdminService memberAdminService;

    public ProjectMemberController(MemberAdminService memberAdminService) {
        this.memberAdminService = memberAdminService;
    }

    @GetMapping
    public List<ProjectMemberView> list(@PathVariable String projectId) {
        return memberAdminService.listProjectMembers(AuthContext.require(), projectId);
    }

    @PostMapping
    public ProjectMemberView invite(@PathVariable String projectId, @RequestBody InviteMemberRequest request) {
        return memberAdminService.inviteToProject(AuthContext.require(), projectId, request);
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> remove(@PathVariable String projectId, @PathVariable String userId) {
        memberAdminService.removeProjectMember(AuthContext.require(), projectId, userId);
        return Map.of("removed", true, "userId", userId);
    }
}
