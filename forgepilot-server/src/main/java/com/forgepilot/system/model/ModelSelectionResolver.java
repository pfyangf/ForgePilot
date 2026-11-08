package com.forgepilot.system.model;

/**
 * 任务覆盖优先，其次项目默认，再次 Agent 配置。
 * 选中的是目录中的 Provider 标识和模型标识，不携带密钥。
 */
public final class ModelSelectionResolver {

    private ModelSelectionResolver() {
    }

    public static ModelSelection resolve(
            String taskProviderId,
            String taskModelId,
            String projectProviderId,
            String projectModelId,
            String agentProviderId,
            String agentModelId) {
        if (present(taskProviderId)) {
            return new ModelSelection(taskProviderId, taskModelId, "TASK");
        }
        if (present(projectProviderId)) {
            return new ModelSelection(projectProviderId, projectModelId, "PROJECT");
        }
        if (present(agentProviderId)) {
            return new ModelSelection(agentProviderId, agentModelId, "AGENT");
        }
        throw new IllegalStateException("未选择模型 Provider");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record ModelSelection(String providerId, String modelId, String source) {
    }
}
