package com.forgepilot.delivery.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitRemoteUrlTest {

    @Test
    void detectsHttpsAndSshGithubRemotes() {
        assertGithub("https://github.com/pfyangf/forgepilot-demo99.git", "pfyangf", "forgepilot-demo99");
        assertGithub("https://github.com/pfyangf/forgepilot-demo99", "pfyangf", "forgepilot-demo99");
        assertGithub("https://www.github.com/pfyangf/forgepilot-demo99.git", "pfyangf", "forgepilot-demo99");
        assertGithub("git@github.com:pfyangf/forgepilot-demo99.git", "pfyangf", "forgepilot-demo99");
        assertGithub("ssh://git@github.com/pfyangf/forgepilot-demo99.git", "pfyangf", "forgepilot-demo99");
        assertGithub("git://github.com/pfyangf/forgepilot-demo99.git", "pfyangf", "forgepilot-demo99");
    }

    @Test
    void doesNotTreatGitlabOrFileAsGithub() {
        assertThat(GitRemoteUrl.isGitHub("https://gitlab.example.com/group/repo.git")).isFalse();
        assertThat(GitRemoteUrl.detectProvider("https://gitlab.example.com/group/repo.git")).isNull();
        assertThat(GitRemoteUrl.isGitHub("git@gitlab.example.com:group/repo.git")).isFalse();
        assertThat(GitRemoteUrl.isFile("file:///tmp/demo.git")).isTrue();
        assertThat(GitRemoteUrl.isGitHub("file:///tmp/demo.git")).isFalse();
        assertThat(GitRemoteUrl.detectProvider("file:///tmp/demo.git")).isNull();
        assertThat(GitRemoteUrl.parse("file:///tmp/demo.git").isFile()).isTrue();
    }

    @Test
    void rejectsBlankUrls() {
        assertThatThrownBy(() -> GitRemoteUrl.parse("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(GitRemoteUrl.tryParse("")).isNull();
        assertThat(GitRemoteUrl.detectProvider(null)).isNull();
    }

    private static void assertGithub(String url, String owner, String repo) {
        GitRemoteUrl parsed = GitRemoteUrl.parse(url);
        assertThat(parsed.isGitHub()).isTrue();
        assertThat(parsed.owner()).isEqualTo(owner);
        assertThat(parsed.repo()).isEqualTo(repo);
        assertThat(parsed.ownerRepo()).isEqualTo(owner + "/" + repo);
        assertThat(GitRemoteUrl.detectProvider(url)).isEqualTo("GITHUB");
    }
}
