package com.forgepilot.delivery.adapter.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgepilot.delivery.port.GitHostingPort;
import org.junit.jupiter.api.Test;

class GitLabGitHostingAdapterTest {

    @Test
    void stubUsesPlatformTypesAndDoesNotCallGitLab() {
        var adapter = new GitLabGitHostingAdapter();
        assertThat(adapter.providerType()).isEqualTo("gitlab");
        var command = new GitHostingPort.CreateDraft(
                "task-1",
                new GitHostingPort.RepositoryRef("repo-1", "gitlab", "ext", "https://example/repo.git"),
                "fp/task-1",
                "abc",
                "main",
                "task-1:repo-1:abc");
        assertThatThrownBy(() -> adapter.createOrGetDraft(command)).isInstanceOf(UnsupportedOperationException.class);
    }
}
