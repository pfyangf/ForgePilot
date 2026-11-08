package com.forgepilot.delivery.adapter.gitlab;

import java.util.Map;

/** 可注入的 GitLab HTTP 客户端。令牌只走请求头，禁止拼进 URL。 */
public interface GitLabApiClient {

    Response exchange(String method, String url, String jsonBody, Map<String, String> query, String privateToken);

    record Response(int statusCode, String body) {
        public boolean is2xx() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
