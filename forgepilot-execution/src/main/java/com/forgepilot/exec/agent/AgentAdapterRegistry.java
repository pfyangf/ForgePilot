package com.forgepilot.exec.agent;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentAdapterRegistry {

    private final List<AgentAdapter> adapters;

    public AgentAdapterRegistry(List<AgentAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public List<AgentAdapter> all() {
        return adapters;
    }

    public AgentAdapter mvp() {
        return adapters.stream()
                .filter(adapter -> adapter.maturity() == AgentAdapter.AdapterMaturity.MVP_STUB)
                .reduce((first, second) -> {
                    throw new IllegalStateException("MVP 适配器只能有一个");
                })
                .orElseThrow(() -> new IllegalStateException("缺少 MVP 适配器"));
    }
}
