package com.forgepilot.delivery.adapter.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.delivery.port.GitHostingPort;
import com.forgepilot.delivery.port.GitHostingPort.CreateDraft;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitLabGitHostingAdapterTest {

    @Test
    void createOrGetDraftIsIdempotentAndKeepsTokenOutOfUrl() {
        List<GitLabApiClient.Response> script = new ArrayList<>();
        // first list empty
        script.add(new GitLabApiClient.Response(200, "[]"));
        // create
        script.add(new GitLabApiClient.Response(
                201,
                """
                {"iid":42,"web_url":"https://gitlab.example/mr/42","source_branch":"fp/t1","state":"opened","diff_refs":{"head_sha":"abc123"}}
                """));
        // second call list finds existing
        script.add(new GitLabApiClient.Response(
                200,
                """
                [{"iid":42,"web_url":"https://gitlab.example/mr/42","source_branch":"fp/t1","state":"opened","diff_refs":{"head_sha":"abc123"}}]
                """));

        AtomicInteger idx = new AtomicInteger();
        List<String> urls = new ArrayList<>();
        GitLabApiClient client = (method, url, body, query, token) -> {
            urls.add(url);
            assertThat(url).doesNotContain(token);
            assertThat(token).isEqualTo("secret-token");
            return script.get(idx.getAndIncrement());
        };

        var adapter = new GitLabGitHostingAdapter(client, new ObjectMapper(), "secret-token");
        assertThat(adapter.supports("gitlab")).isTrue();
        RepositoryRef ref = new RepositoryRef("repo-1", "GITLAB", "99", "https://gitlab.example.com/demo/pay.git");
        CreateDraft cmd = new CreateDraft("t1", ref, "fp/t1", "abc123", "main", "Draft", "t1:repo-1");
        var first = adapter.createOrGetDraft(cmd);
        var second = adapter.createOrGetDraft(cmd);
        assertThat(first.externalId()).isEqualTo("42");
        assertThat(second.externalId()).isEqualTo("42");
        assertThat(first.url()).contains("/mr/42");
        assertThat(urls).isNotEmpty();
    }

    @Test
    void jdkClientRejectsTokenInUrl() {
        var client = new JdkGitLabApiClient();
        assertThatThrownBy(() -> client.exchange("GET", "https://x/secret-token/y", null, Map.of(), "secret-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
