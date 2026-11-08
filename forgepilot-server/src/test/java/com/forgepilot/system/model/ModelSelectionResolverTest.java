package com.forgepilot.system.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelSelectionResolverTest {

    @Test
    void taskOverrideWinsOverProjectAndAgent() {
        var selection = ModelSelectionResolver.resolve("task-p", "task-model", "project-p", "project-model", "agent-p", "agent-model");
        assertThat(selection.source()).isEqualTo("TASK");
        assertThat(selection.providerId()).isEqualTo("task-p");
        assertThat(selection.modelId()).isEqualTo("task-model");
    }

    @Test
    void projectDefaultIsUsedWhenTaskDoesNotOverride() {
        var selection = ModelSelectionResolver.resolve(null, null, "project-p", "project-model", "agent-p", "agent-model");
        assertThat(selection.source()).isEqualTo("PROJECT");
        assertThat(selection.providerId()).isEqualTo("project-p");
    }

    @Test
    void agentProfileIsTheLastFallback() {
        var selection = ModelSelectionResolver.resolve(" ", null, "", null, "agent-p", "agent-model");
        assertThat(selection.source()).isEqualTo("AGENT");
        assertThat(selection.modelId()).isEqualTo("agent-model");
    }

    @Test
    void missingSelectionFails() {
        assertThatThrownBy(() -> ModelSelectionResolver.resolve(null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
