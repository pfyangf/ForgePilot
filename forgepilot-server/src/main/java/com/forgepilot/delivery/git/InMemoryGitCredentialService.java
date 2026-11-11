package com.forgepilot.delivery.git;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGitCredentialService implements GitCredentialPort {

    public static final String GITHUB_PUSH_TOKEN_REQUIRED = "推送 GitHub 仓库需要配置 FORGEPILOT_GITHUB_TOKEN";
    public static final String GITLAB_PUSH_TOKEN_REQUIRED = "推送 GitLab 仓库需要配置 FORGEPILOT_GITLAB_TOKEN";

    private final ConcurrentHashMap<String, IssuedCredential> byId = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final String gitlabToken;
    private final String githubToken;

    public InMemoryGitCredentialService(long ttlSeconds) {
        this(ttlSeconds, "", "");
    }

    @Autowired
    public InMemoryGitCredentialService(
            @Value("${forgepilot.git.token-ttl-seconds:900}") long ttlSeconds,
            @Value("${forgepilot.gitlab.token:}") String gitlabToken,
            @Value("${forgepilot.github.token:}") String githubToken) {
        this.ttlSeconds = ttlSeconds;
        this.gitlabToken = gitlabToken == null ? "" : gitlabToken;
        this.githubToken = githubToken == null ? "" : githubToken;
    }

    @Override
    public IssuedCredential issue(String projectId, Set<String> gitHosts) {
        String id = UUID.randomUUID().toString();
        String token = "fp_git_" + UUID.randomUUID().toString().replace("-", "");
        IssuedCredential cred = new IssuedCredential(
                id, token, Instant.now().plusSeconds(ttlSeconds), gitHosts == null ? Set.of() : Set.copyOf(gitHosts));
        byId.put(id, cred);
        return cred;
    }

    @Override
    public void revoke(String credentialId) {
        byId.remove(credentialId);
    }

    public IssuedCredential get(String credentialId) {
        return byId.get(credentialId);
    }

    @Override
    public String askPassTokenFor(String cloneUrl) {
        if (GitRemoteUrl.isFile(cloneUrl)) {
            return null;
        }
        GitRemoteUrl parsed = GitRemoteUrl.tryParse(cloneUrl);
        if (parsed != null && parsed.isGitHub()) {
            return blankToNull(githubToken);
        }
        return blankToNull(gitlabToken);
    }

    @Override
    public void requireAskPassTokenForPush(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank() || GitRemoteUrl.isFile(cloneUrl)) {
            return;
        }
        String token = askPassTokenFor(cloneUrl);
        if (token != null) {
            return;
        }
        if (GitRemoteUrl.isGitHub(cloneUrl)) {
            throw new IllegalStateException(GITHUB_PUSH_TOKEN_REQUIRED);
        }
        throw new IllegalStateException(GITLAB_PUSH_TOKEN_REQUIRED);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
