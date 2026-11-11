package com.forgepilot.delivery.adapter.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.delivery.git.GitRemoteUrl;
import com.forgepilot.delivery.port.GitHostingPort;
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
 * GitHub 适配器。领域只看到 {@link GitHostingPort}；令牌只经 Authorization 头传递。
 */
@Component
public class GitHubGitHostingAdapter implements GitHostingPort {

    public static final String PROVIDER_TYPE = "GITHUB";
    public static final String TOKEN_REQUIRED_MESSAGE = "创建 GitHub Draft PR 需要配置 FORGEPILOT_GITHUB_TOKEN";

    private final GitHubApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final Supplier<String> tokenSupplier;

    public GitHubGitHostingAdapter(
            GitHubApiClient apiClient,
            ObjectMapper objectMapper,
            @Value("${forgepilot.github.token:}") String defaultToken) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        String token = defaultToken == null ? "" : defaultToken;
        this.tokenSupplier = () -> token;
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
        JsonNode json = require2xx(get(ref, repoApi(ref), Map.of()), "读取仓库");
        return new RepositoryView(
                ref.repositoryId(),
                PROVIDER_TYPE,
                json.hasNonNull("id") ? json.get("id").asText() : ref.externalId(),
                text(json, "default_branch"));
    }

    @Override
    public List<BranchView> listBranches(RepositoryRef ref) {
        JsonNode json = require2xx(get(ref, repoApi(ref) + "/branches", Map.of()), "列出分支");
        List<BranchView> branches = new ArrayList<>();
        if (json.isArray()) {
            for (JsonNode node : json) {
                branches.add(new BranchView(text(node, "name"), text(node.path("commit"), "sha")));
            }
        }
        return branches;
    }

    @Override
    public DiffView compare(RepositoryRef ref, String from, String to) {
        String path = repoApi(ref) + "/compare/" + enc(from) + "..." + enc(to);
        JsonNode json = require2xx(get(ref, path, Map.of()), "比较分支");
        List<String> paths = new ArrayList<>();
        JsonNode files = json.path("files");
        if (files.isArray()) {
            for (JsonNode file : files) {
                if (file.hasNonNull("filename")) {
                    paths.add(file.get("filename").asText());
                }
            }
        }
        return new DiffView(from, to, "compare " + from + ".." + to + " files=" + paths.size(), List.copyOf(paths));
    }

    @Override
    public BranchView ensureBranch(EnsureBranch command) {
        requireToken("创建 GitHub 分支需要配置 FORGEPILOT_GITHUB_TOKEN");
        RepositoryRef ref = command.repository();
        String path = repoApi(ref) + "/git/ref/heads/" + enc(command.branchName());
        GitHubApiClient.Response existing = exchange(ref, "GET", path, null, Map.of());
        if (existing.is2xx()) {
            JsonNode json = readTree(existing.body());
            return new BranchView(command.branchName(), text(json.path("object"), "sha"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ref", "refs/heads/" + command.branchName());
        body.put("sha", command.startPointSha());
        GitHubApiClient.Response created = exchange(ref, "POST", repoApi(ref) + "/git/refs", writeJson(body), Map.of());
        if (!created.is2xx()) {
            throw new IllegalStateException("GitHub 创建分支失败: HTTP " + created.statusCode());
        }
        JsonNode json = readTree(created.body());
        return new BranchView(command.branchName(), text(json.path("object"), "sha"));
    }

    @Override
    public MergeRequestView createOrGetDraft(CreateDraft command) {
        requireToken(TOKEN_REQUIRED_MESSAGE);
        RepositoryRef ref = command.repository();
        GitRemoteUrl parsed = GitRemoteUrl.parse(ref.cloneUrl());
        String owner = parsed.owner();
        String listPath = repoApi(ref) + "/pulls";
        String head = owner + ":" + command.branchName();
        GitHubApiClient.Response listed =
                exchange(ref, "GET", listPath, null, Map.of("state", "open", "head", head));
        if (listed.is2xx()) {
            JsonNode arr = readTree(listed.body());
            if (arr.isArray() && !arr.isEmpty()) {
                return toPrView(ref.repositoryId(), arr.get(0), command.idempotencyKey(), command.headSha());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("head", command.branchName());
        body.put("base", command.targetBranch());
        body.put(
                "title",
                command.title() == null || command.title().isBlank()
                        ? "Draft: " + command.branchName()
                        : command.title());
        body.put("draft", true);
        GitHubApiClient.Response created = exchange(ref, "POST", listPath, writeJson(body), Map.of());
        if (created.statusCode() == 422) {
            GitHubApiClient.Response again =
                    exchange(ref, "GET", listPath, null, Map.of("state", "open", "head", head));
            JsonNode arr = readTree(again.body());
            if (arr.isArray() && !arr.isEmpty()) {
                return toPrView(ref.repositoryId(), arr.get(0), command.idempotencyKey(), command.headSha());
            }
        }
        if (!created.is2xx()) {
            throw new IllegalStateException("GitHub 创建 Draft PR 失败: HTTP " + created.statusCode());
        }
        return toPrView(ref.repositoryId(), readTree(created.body()), command.idempotencyKey(), command.headSha());
    }

    @Override
    public MergeRequestView getMergeRequest(MergeRequestRef ref) {
        throw new UnsupportedOperationException("getMergeRequest 需要完整 RepositoryRef，请使用重载方法");
    }

    public MergeRequestView getMergeRequest(RepositoryRef repository, String externalNumber) {
        JsonNode json = require2xx(
                get(repository, repoApi(repository) + "/pulls/" + enc(externalNumber), Map.of()), "读取 Pull Request");
        String head = text(json.path("head"), "sha");
        return toPrView(repository.repositoryId(), json, null, head);
    }

    private MergeRequestView toPrView(String repositoryId, JsonNode json, String idempotencyKey, String headSha) {
        String sha = headSha == null ? "" : headSha;
        if (sha.isBlank()) {
            sha = text(json.path("head"), "sha");
        }
        String state = text(json, "state");
        if ("open".equalsIgnoreCase(state)) {
            state = "OPEN";
        } else if (!state.isBlank()) {
            state = state.toUpperCase(Locale.ROOT);
        } else {
            state = "OPEN";
        }
        String externalId = json.hasNonNull("number") ? json.get("number").asText() : text(json, "id");
        return new MergeRequestView(
                repositoryId,
                externalId,
                text(json, "html_url"),
                text(json.path("head"), "ref"),
                sha,
                idempotencyKey,
                state);
    }


    @Override
    public MergeResult mergeBranch(MergeBranch command) {
        requireToken("合并 GitHub 分支需要配置 FORGEPILOT_GITHUB_TOKEN");
        RepositoryRef ref = command.repository();
        if (command.sourceBranch() == null || command.sourceBranch().isBlank()) {
            throw new IllegalStateException("缺少源分支，无法合并");
        }
        if (command.targetBranch() == null || command.targetBranch().isBlank()) {
            throw new IllegalStateException("缺少目标分支，无法合并");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("base", command.targetBranch());
        body.put("head", command.sourceBranch());
        if (command.commitMessage() != null && !command.commitMessage().isBlank()) {
            body.put("commit_message", command.commitMessage());
        }
        GitHubApiClient.Response response =
                exchange(ref, "POST", repoApi(ref) + "/merges", writeJson(body), Map.of());
        if (response.statusCode() == 204) {
            return new MergeResult(
                    command.sourceBranch(), command.targetBranch(), "", true, "目标分支已包含源分支");
        }
        if (response.statusCode() == 409) {
            throw new IllegalStateException(
                    "GitHub 合并冲突：无法将 " + command.sourceBranch() + " 合并进 " + command.targetBranch());
        }
        if (response.statusCode() == 404) {
            throw new IllegalStateException(
                    "GitHub 找不到分支 " + command.sourceBranch() + " 或 " + command.targetBranch());
        }
        if (!response.is2xx()) {
            throw new IllegalStateException("GitHub 合并失败: HTTP " + response.statusCode());
        }
        JsonNode json = readTree(response.body());
        String sha = text(json, "sha");
        return new MergeResult(command.sourceBranch(), command.targetBranch(), sha, false, "已合并");
    }

    private String requireToken(String message) {
        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(message);
        }
        return token;
    }

    private JsonNode require2xx(GitHubApiClient.Response response, String action) {
        if (!response.is2xx()) {
            throw new IllegalStateException("GitHub " + action + " 失败: HTTP " + response.statusCode());
        }
        return readTree(response.body());
    }

    private GitHubApiClient.Response get(RepositoryRef ref, String path, Map<String, String> query) {
        return exchange(ref, "GET", path, null, query);
    }

    private GitHubApiClient.Response exchange(
            RepositoryRef ref, String method, String path, String body, Map<String, String> query) {
        String url = path.startsWith("http") ? path : apiBase(ref.cloneUrl()) + path;
        String token = tokenSupplier.get();
        return apiClient.exchange(method, url, body, query, token);
    }

    private String apiBase(String cloneUrl) {
        GitRemoteUrl parsed = GitRemoteUrl.parse(cloneUrl);
        if (parsed.isGitHub()) {
            return "https://api.github.com";
        }
        if (parsed.host() == null || parsed.host().isBlank()) {
            throw new IllegalStateException("无法从仓库地址解析 GitHub 主机: " + cloneUrl);
        }
        return "https://" + parsed.host() + "/api/v3";
    }

    private String repoApi(RepositoryRef ref) {
        GitRemoteUrl parsed = GitRemoteUrl.parse(ref.cloneUrl());
        if (parsed.ownerRepo().isBlank()) {
            throw new IllegalStateException("无法从仓库地址解析 GitHub owner/repo: " + ref.cloneUrl());
        }
        return "/repos/" + enc(parsed.owner()) + "/" + enc(parsed.repo());
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "null" : body);
        } catch (Exception ex) {
            throw new IllegalStateException("解析 GitHub JSON 失败", ex);
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
