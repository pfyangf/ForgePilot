package com.forgepilot.system.web;

import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.AiDraftService;
import com.forgepilot.system.service.AiDraftService.DraftView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiDraftController {

    private final AiDraftService aiDraftService;

    public AiDraftController(AiDraftService aiDraftService) {
        this.aiDraftService = aiDraftService;
    }

    @PostMapping("/drafts")
    public DraftView generate(@Valid @RequestBody GenerateDraftRequest request) {
        return aiDraftService.generate(AuthContext.require(), request);
    }
}
