package com.forgepilot.system.guidance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务启动时把平台、租户、项目三层规约合成一份。
 * 标题相同视为冲突，更具体的一层覆盖上一层；标题不同的规则全部保留。
 * 未启用，或未标记进入 Agent 上下文的条目不会注入。
 */
public final class ConventionComposer {

    private ConventionComposer() {
    }

    public static List<ConventionItem> forAgent(List<ConventionItem> items) {
        Map<String, ConventionItem> composed = new LinkedHashMap<>();
        for (String scope : List.of("PLATFORM", "TENANT", "PROJECT")) {
            for (ConventionItem item : items) {
                if (!scope.equals(item.scope()) || !item.enabled() || !item.includeInAgent()) {
                    continue;
                }
                composed.put(item.title(), item);
            }
        }
        return new ArrayList<>(composed.values());
    }

    public record ConventionItem(String scope, String title, String body, boolean enabled, boolean includeInAgent) {
    }
}
