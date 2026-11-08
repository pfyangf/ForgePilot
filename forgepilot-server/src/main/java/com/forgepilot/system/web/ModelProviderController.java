package com.forgepilot.system.web;

import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.ModelProviderCatalogService;
import com.forgepilot.system.service.ModelProviderCatalogService.ProviderView;
import com.forgepilot.system.service.ModelProviderCatalogService.UpsertRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-providers")
public class ModelProviderController {

    private final ModelProviderCatalogService service;

    public ModelProviderController(ModelProviderCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProviderView> list() {
        return service.list(AuthContext.require());
    }

    @GetMapping("/{id}")
    public ProviderView get(@PathVariable String id) {
        return service.get(AuthContext.require(), id);
    }

    @PostMapping
    public ProviderView create(@Valid @RequestBody ProviderBody body) {
        return service.create(AuthContext.require(), body.toUpsert());
    }

    @PutMapping("/{id}")
    public ProviderView update(@PathVariable String id, @Valid @RequestBody ProviderBody body) {
        return service.update(AuthContext.require(), id, body.toUpsert());
    }

    public record ProviderBody(
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 64) String providerType,
            @NotBlank @Size(max = 512) String baseUrl,
            Boolean enabled,
            @Size(max = 64) String apiVersion,
            @NotBlank String upstreamWireApi,
            @Size(max = 64) String translationGateway,
            @Size(max = 4096) String apiKey) {
        UpsertRequest toUpsert() {
            return new UpsertRequest(
                    name, providerType, baseUrl, enabled, apiVersion, upstreamWireApi, translationGateway, apiKey);
        }
    }
}
