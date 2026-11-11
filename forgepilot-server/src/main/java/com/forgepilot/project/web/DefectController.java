package com.forgepilot.project.web;

import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.DefectView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/defects")
public class DefectController {

    private final RequirementLifecycleService lifecycleService;

    public DefectController(RequirementLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/{defectId}/close")
    public DefectView close(@PathVariable String defectId, @RequestBody(required = false) @Valid DefectActionRequest request) {
        return lifecycleService.closeDefect(AuthContext.require(), defectId, request == null ? new DefectActionRequest(null) : request);
    }

    @PostMapping("/{defectId}/reject")
    public DefectView reject(@PathVariable String defectId, @RequestBody(required = false) @Valid DefectActionRequest request) {
        return lifecycleService.rejectDefect(AuthContext.require(), defectId, request == null ? new DefectActionRequest(null) : request);
    }

    @PostMapping("/{defectId}/verify")
    public DefectView verify(@PathVariable String defectId, @RequestBody(required = false) @Valid DefectActionRequest request) {
        return lifecycleService.verifyDefect(AuthContext.require(), defectId, request == null ? new DefectActionRequest(null) : request);
    }
}
