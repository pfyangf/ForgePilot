package com.forgepilot.delivery.adapter.github;

import java.util.Map;

/** 可注入的 GitHub HTTP 客户端。令牌只走 Authorization 头，禁止拼进 URL。 */
public interface GitHubApiClient {

    Response exchange(String method, String url, String jsonBody, Map<String, String> query, String token);

    record Response(int statusCode, String body) {
        public boolean is2xx() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
