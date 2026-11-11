package com.forgepilot.delivery.adapter.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.delivery.port.GitHostingPort.CreateDraft;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitHubGitHostingAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void createOrGetDraftPostsDraftTrueAndKeepsTokenOutOfUrl() throws Exception {
        List<GitHubApiClient.Response> script = new ArrayList<>();
        script.add(new GitHubApiClient.Response(200, "[]"));
        script.add(new GitHubApiClient.Response(
                201,
                """
                {"number":7,"html_url":"https://github.com/pfyangf/forgepilot-demo99/pull/7","state":"open","head":{"ref":"fp/t1","sha":"abc123"}}
                """));
        script.add(new GitHubApiClient.Response(
                200,
                """
                [{"number":7,"html_url":"https://github.com/pfyangf/forgepilot-demo99/pull/7","state":"open","head":{"ref":"fp/t1","sha":"abc123"}}]
                """));

        AtomicInteger idx = new AtomicInteger();
        List<String> urls = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        GitHubApiClient client = (method, url, body, query, token) -> {
            methods.add(method);
            urls.add(url);
            bodies.add(body);
            assertThat(url).doesNotContain(token);
            assertThat(url).startsWith("https://api.github.com/repos/pfyangf/forgepilot-demo99/");
            assertThat(token).isEqualTo("ghp_secret-token");
            if ("GET".equals(method) && query != null) {
                assertThat(query.get("head")).isEqualTo("pfyangf:fp/t1");
                assertThat(query.get("state")).isEqualTo("open");
            }
            return script.get(idx.getAndIncrement());
        };

        var adapter = new GitHubGitHostingAdapter(client, MAPPER, "ghp_secret-token");
        assertThat(adapter.supports("github")).isTrue();
        assertThat(adapter.supports("GITLAB")).isFalse();
        RepositoryRef ref = new RepositoryRef(
                "repo-1", "GITHUB", null, "https://github.com/pfyangf/forgepilot-demo99.git");
        CreateDraft cmd = new CreateDraft("t1", ref, "fp/t1", "abc123", "main", "Draft: demo", "t1:repo-1");
        var first = adapter.createOrGetDraft(cmd);
        var second = adapter.createOrGetDraft(cmd);

        assertThat(first.externalId()).isEqualTo("7");
        assertThat(second.externalId()).isEqualTo("7");
        assertThat(first.url()).isEqualTo("https://github.com/pfyangf/forgepilot-demo99/pull/7");
        assertThat(first.state()).isEqualTo("OPEN");
        assertThat(methods).contains("POST");
        String postBody = bodies.stream().filter(b -> b != null && !b.isBlank()).findFirst().orElseThrow();
        JsonNode json = MAPPER.readTree(postBody);
        assertThat(json.get("draft").asBoolean()).isTrue();
        assertThat(json.get("head").asText()).isEqualTo("fp/t1");
        assertThat(json.get("base").asText()).isEqualTo("main");
        assertThat(json.get("title").asText()).isEqualTo("Draft: demo");
        assertThat(urls).isNotEmpty();
        assertThat(urls.stream().noneMatch(u -> u.contains("ghp_secret-token"))).isTrue();
    }

    @Test
    void createOrGetDraftUsesGitAtUrlAndFailsClearlyWithoutToken() {
        GitHubApiClient client = (method, url, body, query, token) -> {
            throw new AssertionError("missing token must not call HTTP");
        };
        var adapter = new GitHubGitHostingAdapter(client, MAPPER, "");
        RepositoryRef ref = new RepositoryRef(
                "repo-1", "GITHUB", null, "git@github.com:pfyangf/forgepilot-demo99.git");
        CreateDraft cmd = new CreateDraft("t1", ref, "fp/t1", "abc123", "main", "Draft", "t1:repo-1");
        assertThatThrownBy(() -> adapter.createOrGetDraft(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORGEPILOT_GITHUB_TOKEN");
    }

    @Test
    void jdkClientRejectsTokenInUrl() {
        var client = new JdkGitHubApiClient();
        assertThatThrownBy(() -> client.exchange("GET", "https://x/ghp_secret-token/y", null, Map.of(), "ghp_secret-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
