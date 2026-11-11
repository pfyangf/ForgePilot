package com.forgepilot.delivery.adapter.github;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class JdkGitHubApiClient implements GitHubApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public Response exchange(String method, String url, String jsonBody, Map<String, String> query, String token) {
        try {
            if (token != null && !token.isBlank() && url != null && url.contains(token)) {
                throw new IllegalArgumentException("禁止把 Git 令牌放进 URL");
            }
            String full = appendQuery(url, query);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(full)).timeout(Duration.ofSeconds(30));
            builder.header("Accept", "application/vnd.github+json");
            builder.header("User-Agent", "ForgePilot");
            builder.header("X-GitHub-Api-Version", "2022-11-28");
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            String upper = method.toUpperCase(Locale.ROOT);
            if ("GET".equals(upper) || "DELETE".equals(upper)) {
                builder.method(upper, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(upper, HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("调用 GitHub HTTP 失败: " + ex.getMessage(), ex);
        }
    }

    private static String appendQuery(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        query.forEach((k, v) -> joiner.add(enc(k) + "=" + enc(v)));
        return url + (url.contains("?") ? "&" : "?") + joiner;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
