package com.forgepilot.project.web;

import com.forgepilot.project.workbench.WorkbenchRequirementItem;
import com.forgepilot.project.workbench.WorkbenchScope;
import com.forgepilot.project.workbench.WorkbenchService;
import com.forgepilot.project.workbench.WorkbenchSnapshot;
import com.forgepilot.project.workbench.WorkbenchTaskItem;
import com.forgepilot.system.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping
    public WorkbenchSnapshot home(@RequestParam(defaultValue = "mine") String scope) {
        try {
            return workbenchService.load(AuthContext.require(), WorkbenchScope.from(scope));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/requirements/{id}")
    public WorkbenchRequirementItem requirement(@PathVariable String id) {
        return workbenchService.requirement(AuthContext.require(), id);
    }

    @GetMapping("/tasks/{id}")
    public WorkbenchTaskItem task(@PathVariable String id) {
        return workbenchService.task(AuthContext.require(), id);
    }
}
