package com.forgepilot.delivery.git;

import java.time.Instant;
import java.util.Set;

/** 项目范围短时 Git 令牌。不得写入 remote URL 或长期环境变量。 */
public interface GitCredentialPort {

    IssuedCredential issue(String projectId, Set<String> gitHosts);

    void revoke(String credentialId);

    record IssuedCredential(String credentialId, String token, Instant expiresAt, Set<String> gitHosts) {
    }
}
