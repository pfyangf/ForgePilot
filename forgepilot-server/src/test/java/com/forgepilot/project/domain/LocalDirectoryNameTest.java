package com.forgepilot.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalDirectoryNameTest {

    @Test
    void acceptsStableDirectoryName() {
        assertThat(LocalDirectoryName.isSafe("api-server")).isTrue();
        assertThat(LocalDirectoryName.require("web_app")).isEqualTo("web_app");
    }

    @Test
    void rejectsPathTraversalAndAgentHome() {
        assertThat(LocalDirectoryName.isSafe("../etc")).isFalse();
        assertThat(LocalDirectoryName.isSafe("a/b")).isFalse();
        assertThat(LocalDirectoryName.isSafe(".agent-home")).isFalse();
        assertThat(LocalDirectoryName.isSafe("")).isFalse();
        assertThatThrownBy(() -> LocalDirectoryName.require("..")).isInstanceOf(IllegalArgumentException.class);
    }
}
