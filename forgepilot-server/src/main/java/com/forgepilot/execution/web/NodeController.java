package com.forgepilot.execution.web;

import com.forgepilot.execution.service.NodeRegistryService;
import com.forgepilot.execution.service.NodeRegistryService.Assignment;
import com.forgepilot.execution.service.NodeRegistryService.NodeView;
import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.RoleAccess;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class NodeController {

    private final NodeRegistryService nodeRegistryService;
    private final RoleAccess roleAccess;
    private final boolean internalConfigured;
    private final String internalToken;

    public NodeController(
            NodeRegistryService nodeRegistryService,
            RoleAccess roleAccess,
            @Value("${forgepilot.internal-api.configured:false}") boolean internalConfigured,
            @Value("${forgepilot.internal-api.token:}") String internalToken) {
        this.nodeRegistryService = nodeRegistryService;
        this.roleAccess = roleAccess;
        this.internalConfigured = internalConfigured;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @GetMapping("/nodes")
    public List<NodeView> list() {
        roleAccess.requirePlatformAdmin(AuthContext.require());
        return nodeRegistryService.listNodes();
    }

    @PostMapping("/internal/nodes/register")
    public NodeView register(
            @RequestHeader(value = "X-ForgePilot-Internal-Token", required = false) String token,
            @RequestBody RegisterRequest request) {
        assertInternal(token);
        return nodeRegistryService.register(
                request.nodeKey(),
                request.capacity() == null ? 1 : request.capacity(),
                request.labelsJson(),
                null);
    }

    @PostMapping("/internal/nodes/heartbeat")
    public NodeView heartbeat(
            @RequestHeader(value = "X-ForgePilot-Internal-Token", required = false) String token,
            @RequestBody HeartbeatRequest request) {
        assertInternal(token);
        return nodeRegistryService.heartbeat(request.nodeKey(), request.activeSessions() == null ? 0 : request.activeSessions());
    }

    @PostMapping("/internal/nodes/assign-next")
    public Map<String, Object> assignNext(
            @RequestHeader(value = "X-ForgePilot-Internal-Token", required = false) String token) {
        assertInternal(token);
        Assignment assignment = nodeRegistryService.assignNextPending(null);
        if (assignment == null) {
            return Map.of("assigned", false);
        }
        return Map.of(
                "assigned",
                true,
                "taskId",
                assignment.taskId(),
                "nodeId",
                assignment.nodeId(),
                "nodeKey",
                assignment.nodeKey());
    }

    private void assertInternal(String token) {
        if (!internalConfigured) {
            // 本机开发：未配置内部令牌时放行注册/心跳
            return;
        }
        if (token == null || !token.equals(internalToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部令牌无效");
        }
    }

    public record RegisterRequest(@NotBlank String nodeKey, @Min(1) Integer capacity, String labelsJson) {
    }

    public record HeartbeatRequest(@NotBlank String nodeKey, Integer activeSessions) {
    }
}
