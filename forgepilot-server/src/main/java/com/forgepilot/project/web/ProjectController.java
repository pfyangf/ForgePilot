package com.forgepilot.project.web;

import com.forgepilot.project.service.ProjectService;
import com.forgepilot.project.service.ProjectService.ProjectDetailView;
import com.forgepilot.project.service.ProjectService.ProjectView;
import com.forgepilot.project.service.ProjectService.RepositoryView;
import com.forgepilot.project.service.RequirementService;
import com.forgepilot.project.service.RequirementService.RequirementView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final RequirementService requirementService;

    public ProjectController(ProjectService projectService, RequirementService requirementService) {
        this.projectService = projectService;
        this.requirementService = requirementService;
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

    @GetMapping("/{projectId}/repositories")
    public List<RepositoryView> repositories(@PathVariable String projectId) {
        return projectService.listRepositories(AuthContext.require(), projectId);
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
}
