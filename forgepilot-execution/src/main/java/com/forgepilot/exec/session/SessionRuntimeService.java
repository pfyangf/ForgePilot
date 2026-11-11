package com.forgepilot.exec.session;

import com.forgepilot.exec.agent.AgentAdapter;
import com.forgepilot.exec.agent.AgentAdapterRegistry;
import com.forgepilot.exec.agent.AgentNotAvailableException;
import com.forgepilot.exec.agent.CodexCliAgentAdapter;
import com.forgepilot.exec.log.SessionLogStore;
import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.model.ModelSessionProxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 执行侧 Session 运行时。控制面仍是 Session 状态权威（PostgreSQL）；这里负责代理令牌与 Agent 进程。
 */
@Service
public class SessionRuntimeService {

    private final AgentAdapterRegistry registry;
    private final InMemoryModelSessionProxy modelProxy;
    private final SessionLogStore logStore;
    private final ConcurrentHashMap<String, RuntimeSession> sessions = new ConcurrentHashMap<>();

    public SessionRuntimeService(
            AgentAdapterRegistry registry, InMemoryModelSessionProxy modelProxy, SessionLogStore logStore) {
        this.registry = registry;
        this.modelProxy = modelProxy;
        this.logStore = logStore;
    }

    public StartResult start(StartRequest request) {
        AgentAdapter adapter = registry.mvp();
        ModelSessionProxy.ModelSessionCredential credential = modelProxy.issue(new ModelSessionProxy.ModelSessionRequest(
                request.sessionId(),
                request.providerId(),
                request.modelId(),
                request.upstreamBaseUrl(),
                request.upstreamApiKey(),
                request.upstreamWireApi()));
        Map<String, Object> agentConfig = modelProxy.agentFacingConfig(credential);
        if (agentConfig.containsKey("apiKey") || String.valueOf(agentConfig).contains("upstreamApiKey")) {
            modelProxy.revoke(request.sessionId());
            throw new IllegalStateException("Agent 配置不得包含 Provider apiKey");
        }
        AgentAdapter.AgentExecutionContext context = new AgentAdapter.AgentExecutionContext(
                request.taskId(),
                request.sessionId(),
                request.workspacePath(),
                request.agentHomePath(),
                credential.baseUrl(),
                credential.shortLivedToken(),
                request.modelId(),
                request.prompt());
        try {
            adapter.start(context);
        } catch (AgentNotAvailableException ex) {
            modelProxy.revoke(request.sessionId());
            logStore.append(request.sessionId(), "start failed: " + ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            modelProxy.revoke(request.sessionId());
            logStore.append(request.sessionId(), "start failed: " + ex.getMessage());
            throw ex;
        }
        sessions.put(
                request.sessionId(),
                new RuntimeSession(request.sessionId(), request.taskId(), "RUNNING", credential.baseUrl()));
        logStore.append(request.sessionId(), "session runtime started");
        return new StartResult(
                request.sessionId(),
                "RUNNING",
                credential.baseUrl(),
                credential.shortLivedToken(),
                InMemoryModelSessionProxy.WIRE_API,
                agentConfig,
                adapter.capabilities());
    }

    public void cancel(String sessionId) {
        registry.mvp().cancel(sessionId);
        modelProxy.revoke(sessionId);
        RuntimeSession existing = sessions.get(sessionId);
        if (existing != null) {
            sessions.put(sessionId, new RuntimeSession(sessionId, existing.taskId(), "CANCELLED", existing.proxyBaseUrl()));
        }
        logStore.append(sessionId, "session runtime cancelled; model token revoked");
    }

    public void sendInput(String sessionId, String message) {
        AgentAdapter adapter = registry.mvp();
        if (!adapter.capabilities().interactiveInput()) {
            throw new UnsupportedOperationException("当前适配器不支持交互输入");
        }
        adapter.sendMessage(sessionId, message);
        logStore.append(sessionId, "input accepted");
    }

    public void revokeToken(String sessionId) {
        modelProxy.revoke(sessionId);
        logStore.append(sessionId, "model token revoked");
    }

    public List<String> tailLogs(String sessionId, int limit) {
        return logStore.tail(sessionId, limit);
    }

    public record StartRequest(
            String taskId,
            String sessionId,
            String workspacePath,
            String agentHomePath,
            String providerId,
            String modelId,
            String upstreamBaseUrl,
            String upstreamApiKey,
            String upstreamWireApi,
            String prompt) {
        public StartRequest(
                String taskId,
                String sessionId,
                String workspacePath,
                String agentHomePath,
                String providerId,
                String modelId,
                String upstreamBaseUrl,
                String upstreamApiKey,
                String upstreamWireApi) {
            this(
                    taskId,
                    sessionId,
                    workspacePath,
                    agentHomePath,
                    providerId,
                    modelId,
                    upstreamBaseUrl,
                    upstreamApiKey,
                    upstreamWireApi,
                    null);
        }
    }

    public record StartResult(
            String sessionId,
            String runtimeStatus,
            String modelProxyBaseUrl,
            String shortLivedToken,
            String wireApi,
            Map<String, Object> agentFacingConfig,
            AgentAdapter.AgentCapabilities capabilities) {
    }

    private record RuntimeSession(String sessionId, String taskId, String status, String proxyBaseUrl) {
    }
}
