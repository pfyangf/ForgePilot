package com.forgepilot.project.web;

import com.forgepilot.project.service.ProjectService;
import com.forgepilot.project.service.ProjectService.ProjectDetailView;
import com.forgepilot.project.service.ProjectService.ProjectView;
import com.forgepilot.project.service.ProjectService.RepositoryView;
import com.forgepilot.project.service.ProjectService.UpdateDefaultsRequest;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.ReleasePlanView;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.ReleaseView;
import com.forgepilot.project.service.RepositoryBranchService;
import com.forgepilot.project.service.RepositoryBranchService.BranchListView;
import com.forgepilot.project.service.RequirementService;
import com.forgepilot.project.service.RequirementService.RequirementView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final RequirementService requirementService;
    private final RequirementLifecycleService lifecycleService;
    private final RepositoryBranchService repositoryBranchService;

    public ProjectController(
            ProjectService projectService,
            RequirementService requirementService,
            RequirementLifecycleService lifecycleService,
            RepositoryBranchService repositoryBranchService) {
        this.projectService = projectService;
        this.requirementService = requirementService;
        this.lifecycleService = lifecycleService;
        this.repositoryBranchService = repositoryBranchService;
    }

    @GetMapping
    public List<ProjectView> list() {
        return projectService.list(AuthContext.require());
    }

    @PostMapping
    public ProjectView create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(AuthContext.require(), request);
    }

    @GetMapping("/{projectId}")
    public ProjectDetailView get(@PathVariable String projectId) {
        return projectService.get(AuthContext.require(), projectId);
    }


    @PatchMapping("/{projectId}/defaults")
    public ProjectView updateDefaults(
            @PathVariable String projectId, @RequestBody UpdateDefaultsRequest request) {
        return projectService.updateDefaults(AuthContext.require(), projectId, request);
    }

    @GetMapping("/{projectId}/repositories")
    public List<RepositoryView> repositories(@PathVariable String projectId) {
        return projectService.listRepositories(AuthContext.require(), projectId);
    }

    @GetMapping("/{projectId}/repositories/{repositoryId}/branches")
    public BranchListView repositoryBranches(
            @PathVariable String projectId, @PathVariable String repositoryId) {
        return repositoryBranchService.list(AuthContext.require(), projectId, repositoryId);
    }

    @GetMapping("/{projectId}/release-plan")
    public ReleasePlanView releasePlan(
            @PathVariable String projectId, @RequestParam List<String> requirementIds) {
        return lifecycleService.releasePlan(AuthContext.require(), projectId, requirementIds);
    }

    @PostMapping("/{projectId}/repositories")
    public RepositoryView addRepository(
            @PathVariable String projectId, @Valid @RequestBody AddRepositoryRequest request) {
        return projectService.addRepository(AuthContext.require(), projectId, request);
    }

    @GetMapping("/{projectId}/requirements")
    public List<RequirementView> requirements(@PathVariable String projectId) {
        return requirementService.listByProject(AuthContext.require(), projectId);
    }

    @PostMapping("/{projectId}/requirements")
    public RequirementView createRequirement(
            @PathVariable String projectId, @Valid @RequestBody CreateRequirementRequest request) {
        return requirementService.create(AuthContext.require(), projectId, request);
    }

    @GetMapping("/{projectId}/releases")
    public List<ReleaseView> releases(@PathVariable String projectId) {
        return lifecycleService.listReleases(AuthContext.require(), projectId);
    }

    @PostMapping("/{projectId}/releases")
    public ReleaseView createRelease(
            @PathVariable String projectId, @Valid @RequestBody CreateReleaseRequest request) {
        return lifecycleService.createRelease(AuthContext.require(), projectId, request);
    }
}
