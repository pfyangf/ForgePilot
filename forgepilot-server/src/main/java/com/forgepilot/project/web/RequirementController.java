package com.forgepilot.project.web;

import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskDefaultsView;
import com.forgepilot.execution.service.TaskService.TaskView;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.ReleasePlanView;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.DefectView;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.DocumentView;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.RequirementViewStatus;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.ReviewView;
import com.forgepilot.project.pmo.PmoProposalService;
import com.forgepilot.project.pmo.PmoProposalView;
import com.forgepilot.project.pmo.PmoProposalView.ConfirmResult;
import com.forgepilot.project.pmo.PmoProposalView.ListView;
import com.forgepilot.project.service.RequirementService;
import com.forgepilot.project.service.RequirementService.RequirementDetailView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService requirementService;
    private final TaskService taskService;
    private final RequirementLifecycleService lifecycleService;
    private final PmoProposalService pmoProposalService;

    public RequirementController(
            RequirementService requirementService,
            TaskService taskService,
            RequirementLifecycleService lifecycleService,
            PmoProposalService pmoProposalService) {
        this.requirementService = requirementService;
        this.taskService = taskService;
        this.lifecycleService = lifecycleService;
        this.pmoProposalService = pmoProposalService;
    }

    @GetMapping("/{requirementId}")
    public RequirementDetailView get(@PathVariable String requirementId) {
        return requirementService.get(AuthContext.require(), requirementId);
    }

    @PostMapping("/{requirementId}/confirm-analysis")
    public RequirementDetailView confirmAnalysis(
            @PathVariable String requirementId, @Valid @RequestBody ConfirmAnalysisRequest request) {
        return requirementService.confirmAnalysis(AuthContext.require(), requirementId, request);
    }

    @GetMapping("/{requirementId}/tasks")
    public List<TaskView> tasks(@PathVariable String requirementId) {
        return taskService.listByRequirement(AuthContext.require(), requirementId);
    }

    @GetMapping("/{requirementId}/task-defaults")
    public TaskDefaultsView taskDefaults(@PathVariable String requirementId) {
        return taskService.defaults(AuthContext.require(), requirementId);
    }

    @GetMapping("/{requirementId}/release-plan")
    public ReleasePlanView releasePlan(@PathVariable String requirementId) {
        var requirement = requirementService.get(AuthContext.require(), requirementId);
        return lifecycleService.releasePlan(AuthContext.require(), requirement.requirement().projectId(), List.of(requirementId));
    }

    @PostMapping("/{requirementId}/tasks")
    public TaskView createTask(@PathVariable String requirementId, @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/reviews")
    public ReviewView reviewRequirement(
            @PathVariable String requirementId, @Valid @RequestBody ReviewDecisionRequest request) {
        return lifecycleService.reviewRequirement(AuthContext.require(), requirementId, request);
    }

    @GetMapping("/{requirementId}/documents")
    public List<DocumentView> documents(@PathVariable String requirementId) {
        requirementService.requireVisible(AuthContext.require(), requirementId);
        return lifecycleService.bundle(requirementId).documents();
    }

    @PostMapping("/{requirementId}/documents")
    public DocumentView saveDocument(
            @PathVariable String requirementId, @Valid @RequestBody SaveDocumentRequest request) {
        return lifecycleService.saveDesignDocument(AuthContext.require(), requirementId, request);
    }

    @PutMapping("/{requirementId}/documents/{documentId}")
    public DocumentView updateDocument(
            @PathVariable String requirementId,
            @PathVariable String documentId,
            @Valid @RequestBody SaveDocumentRequest request) {
        return lifecycleService.saveDesignDocument(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/documents/import")
    public DocumentView importDocument(
            @PathVariable String requirementId, @Valid @RequestBody SaveDocumentRequest request) {
        return lifecycleService.saveDesignDocument(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/documents/{documentId}/submit-review")
    public DocumentView submitDesignReview(@PathVariable String requirementId, @PathVariable String documentId) {
        return lifecycleService.submitDesignReview(AuthContext.require(), requirementId);
    }

    @PostMapping("/{requirementId}/design-reviews")
    public ReviewView reviewDesign(
            @PathVariable String requirementId, @Valid @RequestBody ReviewDecisionRequest request) {
        return lifecycleService.reviewDesignDocument(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/documents/{documentId}/reviews")
    public ReviewView reviewDesignDocument(
            @PathVariable String requirementId,
            @PathVariable String documentId,
            @Valid @RequestBody ReviewDecisionRequest request) {
        return lifecycleService.reviewDesignDocument(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/status")
    public RequirementViewStatus updateStatus(
            @PathVariable String requirementId, @Valid @RequestBody UpdateRequirementStatusRequest request) {
        return lifecycleService.updateStatus(AuthContext.require(), requirementId, request);
    }

    @PostMapping("/{requirementId}/accept")
    public RequirementViewStatus accept(
            @PathVariable String requirementId, @Valid @RequestBody AcceptRequirementRequest request) {
        return lifecycleService.accept(AuthContext.require(), requirementId, request);
    }

    @GetMapping("/{requirementId}/defects")
    public List<DefectView> defects(@PathVariable String requirementId) {
        requirementService.requireVisible(AuthContext.require(), requirementId);
        return lifecycleService.bundle(requirementId).defects();
    }

    @PostMapping("/{requirementId}/defects")
    public DefectView createDefect(
            @PathVariable String requirementId, @Valid @RequestBody CreateDefectRequest request) {
        return lifecycleService.createDefect(AuthContext.require(), requirementId, request);
    }

    @GetMapping("/{requirementId}/proposals")
    public ListView proposals(@PathVariable String requirementId) {
        return pmoProposalService.listForRequirement(AuthContext.require(), requirementId);
    }

    @PostMapping("/{requirementId}/proposals/{proposalId}/confirm")
    public ConfirmResult confirmProposal(
            @PathVariable String requirementId,
            @PathVariable String proposalId,
            @RequestBody(required = false) @Valid ConfirmProposalRequest request) {
        return pmoProposalService.confirm(
                AuthContext.require(),
                requirementId,
                proposalId,
                request == null ? new ConfirmProposalRequest(null, null) : request);
    }

    @PostMapping("/{requirementId}/proposals/{proposalId}/return")
    public PmoProposalView returnProposal(
            @PathVariable String requirementId,
            @PathVariable String proposalId,
            @RequestBody(required = false) @Valid ReturnProposalRequest request) {
        return pmoProposalService.dismiss(
                AuthContext.require(),
                requirementId,
                proposalId,
                request == null ? new ReturnProposalRequest(null) : request);
    }
}
