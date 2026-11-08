package com.forgepilot.delivery.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitAskPassCredentialTest {

    @Test
    void issuedTokenNeverAppearsInCommandLineOrRemoteUrl() {
        InMemoryGitCredentialService creds = new InMemoryGitCredentialService(600);
        var issued = creds.issue("proj-1", Set.of("gitlab.example.com"));
        LocalGitSupport git = new LocalGitSupport();

        assertThatThrownBy(() -> git.run(
                        List.of("git", "clone", "https://oauth2:" + issued.token() + "@gitlab.example.com/a.git"),
                        null,
                        issued.token()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("令牌");

        assertThatThrownBy(() -> git.run(
                        List.of("git", "clone", "https://user:secret-token@gitlab.example.com/a.git"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote URL");

        // Clean URL + askpass token is accepted by the guard (git itself may fail to connect)
        try {
            git.run(List.of("git", "--version"), null, issued.token());
        } catch (IllegalStateException ignored) {
            // older git wrappers still fine; --version should succeed
        }
        List<String> cmd = git.lastCommand();
        assertThat(cmd).isNotEmpty();
        assertThat(String.join(" ", cmd)).doesNotContain(issued.token());
        assertThat(cmd.stream().noneMatch(p -> p.contains(issued.token()))).isTrue();
    }
}
