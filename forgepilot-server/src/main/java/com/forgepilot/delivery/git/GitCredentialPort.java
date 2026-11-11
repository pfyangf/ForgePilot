package com.forgepilot.delivery.git;

import java.time.Instant;
import java.util.Set;

/** 项目范围短时 Git 令牌。不得写入 remote URL 或长期环境变量。 */
public interface GitCredentialPort {

    IssuedCredential issue(String projectId, Set<String> gitHosts);

    void revoke(String credentialId);

    /**
     * 按 clone URL 返回配置的托管令牌，供 GIT_ASKPASS 使用。
     * {@code file://} 或未配置时返回 {@code null}（公开克隆可以不带令牌）。
     */
    String askPassTokenFor(String cloneUrl);

    /**
     * 推送非 {@code file://} 远端时，若对应托管令牌未配置则抛出明确错误。
     */
    void requireAskPassTokenForPush(String cloneUrl);

    record IssuedCredential(String credentialId, String token, Instant expiresAt, Set<String> gitHosts) {
    }
}
