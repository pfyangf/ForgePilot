package com.forgepilot.system.web;

import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.CodingToolCatalogService;
import com.forgepilot.system.service.CodingToolCatalogService.ToolView;
import com.forgepilot.system.service.CodingToolCatalogService.UpsertRequest;
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
@RequestMapping("/api/v1/coding-tools")
public class CodingToolController {

    private final CodingToolCatalogService service;

    public CodingToolController(CodingToolCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolView> list(@RequestParam(defaultValue = "false") boolean readable) {
        if (readable) {
            return service.listReadable(AuthContext.require());
        }
        return service.list(AuthContext.require());
    }

    @GetMapping("/{id}")
    public ToolView get(@PathVariable String id) {
        return service.get(AuthContext.require(), id);
    }

    @PostMapping
    public ToolView create(@Valid @RequestBody ToolBody body) {
        return service.create(AuthContext.require(), body.toUpsert());
    }

    @PutMapping("/{id}")
    public ToolView update(@PathVariable String id, @Valid @RequestBody ToolBody body) {
        return service.update(AuthContext.require(), id, body.toUpsert());
    }

    public record ToolBody(
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 64) String agentType,
            @Size(max = 64) String version,
            @Size(max = 512) String launchCommand,
            String modelProviderId,
            @Size(max = 128) String modelId,
            Boolean interactiveInput,
            Boolean nativePause,
            Boolean interrupt,
            Boolean eventStream,
            Boolean toolEvents,
            Boolean sessionResume,
            Boolean structuredResult,
            Integer timeoutSeconds,
            Boolean enabled) {
        UpsertRequest toUpsert() {
            return new UpsertRequest(
                    name,
                    agentType,
                    version,
                    launchCommand,
                    modelProviderId,
                    modelId,
                    interactiveInput,
                    nativePause,
                    interrupt,
                    eventStream,
                    toolEvents,
                    sessionResume,
                    structuredResult,
                    timeoutSeconds,
                    enabled);
        }
    }
}
