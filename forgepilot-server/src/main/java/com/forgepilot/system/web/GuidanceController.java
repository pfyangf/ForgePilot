package com.forgepilot.system.web;

import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.GuidanceService;
import com.forgepilot.system.service.GuidanceService.CompositionSnapshotView;
import com.forgepilot.system.service.GuidanceService.EffectiveConventions;
import com.forgepilot.system.service.GuidanceService.GuidanceView;
import com.forgepilot.system.service.GuidanceService.UpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GuidanceController {

    private final GuidanceService guidanceService;

    public GuidanceController(GuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @GetMapping("/guidance")
    public List<GuidanceView> list(
            @RequestParam(required = false) String scope, @RequestParam(required = false) String projectId) {
        return guidanceService.list(AuthContext.require(), scope, projectId);
    }

    @GetMapping("/guidance/{id}")
    public GuidanceView get(@PathVariable String id) {
        return guidanceService.get(AuthContext.require(), id);
    }

    @PostMapping("/guidance")
    public GuidanceView create(@Valid @RequestBody GuidanceBody body) {
        return guidanceService.create(AuthContext.require(), body.toUpsert());
    }

    @PutMapping("/guidance/{id}")
    public GuidanceView update(@PathVariable String id, @Valid @RequestBody GuidanceBody body) {
        return guidanceService.update(AuthContext.require(), id, body.toUpsert());
    }

    @GetMapping("/projects/{projectId}/conventions/effective")
    public EffectiveConventions effective(@PathVariable String projectId) {
        return guidanceService.composeForProject(AuthContext.require(), projectId);
    }

    @PostMapping("/projects/{projectId}/conventions/snapshots")
    public CompositionSnapshotView snapshot(@PathVariable String projectId) {
        return guidanceService.snapshotForProject(AuthContext.require(), projectId);
    }

    @GetMapping("/conventions/snapshots/{snapshotId}")
    public CompositionSnapshotView getSnapshot(@PathVariable String snapshotId) {
        return guidanceService.getSnapshot(AuthContext.require(), snapshotId);
    }

    public record GuidanceBody(
            @NotBlank String scope,
            String projectId,
            @NotBlank String kind,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 64) String category,
            @Size(max = 100000) String body,
            Boolean enabled,
            Boolean includeInAgent) {
        UpsertRequest toUpsert() {
            return new UpsertRequest(scope, projectId, kind, title, category, body, enabled, includeInAgent);
        }
    }
}
