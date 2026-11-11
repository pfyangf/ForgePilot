package com.forgepilot.delivery.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.delivery.adapter.github.GitHubApiClient;
import com.forgepilot.delivery.adapter.github.GitHubGitHostingAdapter;
import com.forgepilot.delivery.adapter.gitlab.GitLabApiClient;
import com.forgepilot.delivery.adapter.gitlab.GitLabGitHostingAdapter;
import com.forgepilot.delivery.port.GitHostingPort.CreateDraft;
import com.forgepilot.delivery.port.GitHostingPort.MergeRequestView;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
import org.junit.jupiter.api.Test;

class RoutingGitHostingAdapterTest {

    @Test
    void routesGithubDotComUrlsToGithubEvenWhenProviderSaysGitlab() {
        GitLabGitHostingAdapter gitLab = new GitLabGitHostingAdapter(mock(GitLabApiClient.class), new ObjectMapper(), "gl");
        GitHubApiClient githubClient = mock(GitHubApiClient.class);
        when(githubClient.exchange(
                        org.mockito.ArgumentMatchers.eq("GET"),
                        org.mockito.ArgumentMatchers.contains("/pulls"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("gh")))
                .thenReturn(new GitHubApiClient.Response(
                        200,
                        """
                        [{"number":1,"html_url":"https://github.com/pfyangf/forgepilot-demo99/pull/1","state":"open","head":{"ref":"fp/t1","sha":"abc"}}]
                        """));
        GitHubGitHostingAdapter gitHub = new GitHubGitHostingAdapter(githubClient, new ObjectMapper(), "gh");
        RoutingGitHostingAdapter router = new RoutingGitHostingAdapter(gitLab, gitHub);

        RepositoryRef mislabeled = new RepositoryRef(
                "r1", "GITLAB", null, "https://github.com/pfyangf/forgepilot-demo99.git");
        assertThat(router.adapterFor(mislabeled)).isSameAs(gitHub);

        RepositoryRef ssh = new RepositoryRef(
                "r2", "GITLAB", null, "git@github.com:pfyangf/forgepilot-demo99.git");
        assertThat(router.adapterFor(ssh)).isSameAs(gitHub);

        RepositoryRef explicit = new RepositoryRef(
                "r3", "GITHUB", null, "https://github.com/pfyangf/forgepilot-demo99.git");
        assertThat(router.adapterFor(explicit)).isSameAs(gitHub);

        RepositoryRef gitlab = new RepositoryRef(
                "r4", "GITLAB", "99", "https://gitlab.example.com/demo/pay.git");
        assertThat(router.adapterFor(gitlab)).isSameAs(gitLab);

        RepositoryRef blankProvider = new RepositoryRef(
                "r5", "", null, "https://gitlab.example.com/demo/pay.git");
        assertThat(router.adapterFor(blankProvider)).isSameAs(gitLab);

        CreateDraft cmd = new CreateDraft("t1", mislabeled, "fp/t1", "abc", "main", "Draft", "t1:r1");
        MergeRequestView view = router.createOrGetDraft(cmd);
        assertThat(view.url()).contains("/pull/1");
        assertThat(view.externalId()).isEqualTo("1");
    }

    @Test
    void supportsGitlabAndGithub() {
        RoutingGitHostingAdapter router = new RoutingGitHostingAdapter(
                new GitLabGitHostingAdapter(mock(GitLabApiClient.class), new ObjectMapper(), "gl"),
                new GitHubGitHostingAdapter(mock(GitHubApiClient.class), new ObjectMapper(), "gh"));
        assertThat(router.supports("GITLAB")).isTrue();
        assertThat(router.supports("GITHUB")).isTrue();
        assertThat(router.supports("BITBUCKET")).isFalse();
    }
}
