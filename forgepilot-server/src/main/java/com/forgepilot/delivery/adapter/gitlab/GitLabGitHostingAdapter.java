package com.forgepilot.delivery.adapter.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.delivery.port.GitHostingPort;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitLab 适配器。领域只看到 {@link GitHostingPort}；令牌只经 PRIVATE-TOKEN 头传递。
 */
@Component
public class GitLabGitHostingAdapter implements GitHostingPort {

    public static final String PROVIDER_TYPE = "GITLAB";

    private final GitLabApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final Supplier<String> tokenSupplier;

    public GitLabGitHostingAdapter(
            GitLabApiClient apiClient,
            ObjectMapper objectMapper,
            @Value("${forgepilot.gitlab.token:}") String defaultToken) {
        this(apiClient, objectMapper, () -> defaultToken == null ? "" : defaultToken);
    }

    GitLabGitHostingAdapter(GitLabApiClient apiClient, ObjectMapper objectMapper, Supplier<String> tokenSupplier) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.tokenSupplier = tokenSupplier;
    }

    public String providerType() {
        return PROVIDER_TYPE;
    }

    @Override
    public boolean supports(String providerType) {
        return providerType != null && PROVIDER_TYPE.equalsIgnoreCase(providerType);
    }

    @Override
    public RepositoryView getRepository(RepositoryRef ref) {
        JsonNode json = require2xx(get(ref, "/api/v4/projects/" + projectPath(ref), Map.of()));
        return new RepositoryView(
                ref.repositoryId(),
                PROVIDER_TYPE,
                json.hasNonNull("id") ? json.get("id").asText() : ref.externalId(),
                text(json, "default_branch"));
    }

    @Override
    public List<BranchView> listBranches(RepositoryRef ref) {
        JsonNode json = require2xx(get(ref, "/api/v4/projects/" + projectPath(ref) + "/repository/branches", Map.of()));
        List<BranchView> branches = new ArrayList<>();
        if (json.isArray()) {
            for (JsonNode node : json) {
                branches.add(new BranchView(text(node, "name"), text(node.path("commit"), "id")));
            }
        }
        return branches;
    }

    @Override
    public DiffView compare(RepositoryRef ref, String from, String to) {
        JsonNode json = require2xx(get(
                ref,
                "/api/v4/projects/" + projectPath(ref) + "/repository/compare",
                Map.of("from", from, "to", to)));
        List<String> paths = new ArrayList<>();
        JsonNode diffs = json.path("diffs");
        if (diffs.isArray()) {
            for (JsonNode diff : diffs) {
                if (diff.hasNonNull("new_path")) {
                    paths.add(diff.get("new_path").asText());
                } else if (diff.hasNonNull("old_path")) {
                    paths.add(diff.get("old_path").asText());
                }
            }
        }
        return new DiffView(from, to, "compare " + from + ".." + to + " files=" + paths.size(), List.copyOf(paths));
    }

    @Override
    public BranchView ensureBranch(EnsureBranch command) {
        RepositoryRef ref = command.repository();
        String path = "/api/v4/projects/" + projectPath(ref) + "/repository/branches/" + enc(command.branchName());
        GitLabApiClient.Response existing = exchange(ref, "GET", path, null, Map.of());
        if (existing.is2xx()) {
            JsonNode json = readTree(existing.body());
            return new BranchView(command.branchName(), text(json.path("commit"), "id"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branch", command.branchName());
        body.put("ref", command.startPointSha());
        GitLabApiClient.Response created = exchange(
                ref,
                "POST",
                "/api/v4/projects/" + projectPath(ref) + "/repository/branches",
                writeJson(body),
                Map.of());
        if (!created.is2xx()) {
            throw new IllegalStateException("GitLab 创建分支失败: HTTP " + created.statusCode());
        }
        JsonNode json = readTree(created.body());
        return new BranchView(command.branchName(), text(json.path("commit"), "id"));
    }

    @Override
    public MergeRequestView createOrGetDraft(CreateDraft command) {
        RepositoryRef ref = command.repository();
        String listPath = "/api/v4/projects/" + projectPath(ref) + "/merge_requests";
        GitLabApiClient.Response listed =
                exchange(ref, "GET", listPath, null, Map.of("source_branch", command.branchName(), "state", "opened"));
        if (listed.is2xx()) {
            JsonNode arr = readTree(listed.body());
            if (arr.isArray() && !arr.isEmpty()) {
                return toMrView(ref.repositoryId(), arr.get(0), command.idempotencyKey(), command.headSha());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_branch", command.branchName());
        body.put("target_branch", command.targetBranch());
        body.put("title", command.title() == null || command.title().isBlank()
                ? "Draft: " + command.branchName()
                : command.title());
        body.put("draft", true);
        body.put("remove_source_branch", false);
        GitLabApiClient.Response created = exchange(ref, "POST", listPath, writeJson(body), Map.of());
        if (created.statusCode() == 409 || created.statusCode() == 400) {
            GitLabApiClient.Response again =
                    exchange(ref, "GET", listPath, null, Map.of("source_branch", command.branchName(), "state", "opened"));
            JsonNode arr = readTree(again.body());
            if (arr.isArray() && !arr.isEmpty()) {
                return toMrView(ref.repositoryId(), arr.get(0), command.idempotencyKey(), command.headSha());
            }
        }
        if (!created.is2xx()) {
            throw new IllegalStateException("GitLab 创建 Draft MR 失败: HTTP " + created.statusCode());
        }
        return toMrView(ref.repositoryId(), readTree(created.body()), command.idempotencyKey(), command.headSha());
    }

    @Override
    public MergeRequestView getMergeRequest(MergeRequestRef ref) {
        throw new UnsupportedOperationException("getMergeRequest 需要完整 RepositoryRef，请使用重载方法");
    }

    public MergeRequestView getMergeRequest(RepositoryRef repository, String externalIid) {
        JsonNode json = require2xx(get(
                repository,
                "/api/v4/projects/" + projectPath(repository) + "/merge_requests/" + enc(externalIid),
                Map.of()));
        String head = text(json.path("diff_refs"), "head_sha");
        if (head.isBlank()) {
            head = text(json, "sha");
        }
        return toMrView(repository.repositoryId(), json, null, head);
    }

    private MergeRequestView toMrView(String repositoryId, JsonNode json, String idempotencyKey, String headSha) {
        String sha = headSha == null ? "" : headSha;
        if (sha.isBlank()) {
            sha = text(json.path("diff_refs"), "head_sha");
        }
        String state = text(json, "state");
        if ("opened".equalsIgnoreCase(state)) {
            state = "OPEN";
        } else if (!state.isBlank()) {
            state = state.toUpperCase(Locale.ROOT);
        } else {
            state = "OPEN";
        }
        return new MergeRequestView(
                repositoryId,
                text(json, "iid"),
                text(json, "web_url"),
                text(json, "source_branch"),
                sha,
                idempotencyKey,
                state);
    }

    private JsonNode require2xx(GitLabApiClient.Response response) {
        if (!response.is2xx()) {
            throw new IllegalStateException("GitLab HTTP " + response.statusCode());
        }
        return readTree(response.body());
    }

    private GitLabApiClient.Response get(RepositoryRef ref, String path, Map<String, String> query) {
        return exchange(ref, "GET", path, null, query);
    }

    private GitLabApiClient.Response exchange(
            RepositoryRef ref, String method, String path, String body, Map<String, String> query) {
        String url = path.startsWith("http") ? path : apiBase(ref.cloneUrl()) + path;
        String token = tokenSupplier.get();
        return apiClient.exchange(method, url, body, query, token);
    }

    private String apiBase(String cloneUrl) {
        URI uri = URI.create(cloneUrl);
        String host = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() > 0) {
            host = host + ":" + uri.getPort();
        }
        return host;
    }

    private String projectPath(RepositoryRef ref) {
        if (ref.externalId() != null
                && !ref.externalId().isBlank()
                && ref.externalId().chars().allMatch(Character::isDigit)) {
            return enc(ref.externalId());
        }
        String path = URI.create(ref.cloneUrl()).getPath();
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return enc(path);
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "null" : body);
        } catch (Exception ex) {
            throw new IllegalStateException("解析 GitLab JSON 失败", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? "" : child.asText("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
