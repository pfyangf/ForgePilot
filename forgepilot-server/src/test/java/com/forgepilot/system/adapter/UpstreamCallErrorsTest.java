package com.forgepilot.system.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class UpstreamCallErrorsTest {

    @Test
    void describeTargetUsesHostAndPortAndIgnoresPath() {
        assertThat(UpstreamCallErrors.describeTarget("http://127.0.0.1:18080/v1")).isEqualTo("127.0.0.1:18080");
        assertThat(UpstreamCallErrors.describeTarget("https://api.example.com/v1")).isEqualTo("api.example.com:443");
        assertThat(UpstreamCallErrors.describeTarget("http://localhost/v1")).isEqualTo("localhost:80");
    }

    @Test
    void connectExceptionMentionsHostAndPlainReason() {
        String message = UpstreamCallErrors.modelCallFailed(
                "http://127.0.0.1:18080/v1", new ConnectException("Connection refused"));
        assertThat(message).isEqualTo("模型调用失败：无法连接 127.0.0.1:18080（连接被拒绝，请确认该地址有服务在监听）");
        assertThat(message).doesNotContain("ConnectException");
    }

    @Test
    void wrappedConnectExceptionIsStillExplained() {
        Exception wrapped = new RuntimeException("call failed", new ConnectException("Connection refused"));
        assertThat(UpstreamCallErrors.probeFailed("http://127.0.0.1:18081/v1", wrapped))
                .contains("127.0.0.1:18081")
                .contains("连接被拒绝");
    }

    @Test
    void otherFailuresStayPlainAndNeverLeakSecrets() {
        assertThat(UpstreamCallErrors.describeReason(new UnknownHostException("no-such-host")))
                .isEqualTo("无法解析主机名");
        assertThat(UpstreamCallErrors.describeReason(new HttpTimeoutException("timed out")))
                .isEqualTo("连接超时");
        String sanitized = UpstreamCallErrors.sanitize("upstream said Bearer sk-abcdefghijklmnopqrst and api_key=secret-token");
        assertThat(sanitized).doesNotContain("sk-abcdefghijklmnopqrst");
        assertThat(sanitized).doesNotContain("secret-token");
        assertThat(sanitized).contains("***");
    }
}
