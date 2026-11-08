package com.forgepilot.delivery.git;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGitCredentialService implements GitCredentialPort {

    private final ConcurrentHashMap<String, IssuedCredential> byId = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public InMemoryGitCredentialService(@Value("${forgepilot.git.token-ttl-seconds:900}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
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
}
