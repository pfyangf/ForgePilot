package com.forgepilot.exec.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskWorkspaceLayoutTest {

    private final TaskWorkspaceLayout layout = new TaskWorkspaceLayout();

    @Test
    void concurrentTasksGetDistinctWritablePaths() {
        String first = layout.taskRoot("/var/forgepilot", "task-a");
        String second = layout.taskRoot("/var/forgepilot", "task-b");
        assertThat(first).isNotEqualTo(second);
        assertThat(layout.agentHome(first)).startsWith(first);
        assertThat(layout.agentHome(first)).isNotEqualTo(layout.agentHome(second));
        assertThat(layout.checkout(first, "api")).isEqualTo("/var/forgepilot/workspaces/task-a/api");
        assertThat(layout.mirror("/var/forgepilot", "repo-1")).endsWith("/mirrors/repo-1.git");
    }

    @Test
    void rejectsSharedOrEscapingCheckoutNames() {
        String root = layout.taskRoot("/var/forgepilot", "task-a");
        assertThatThrownBy(() -> layout.checkout(root, "../task-b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.checkout(root, ".agent-home")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.taskRoot("/var/forgepilot", "../x")).isInstanceOf(IllegalArgumentException.class);
    }
}
