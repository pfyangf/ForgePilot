package com.forgepilot.exec.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdkUpstreamResponsesClient implements UpstreamResponsesClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public JdkUpstreamResponsesClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public UpstreamResponse postResponses(String baseUrl, String apiKey, Map<String, Object> body) {
        try {
            String base = baseUrl.replaceAll("/$", "");
            if (!base.endsWith("/v1")) {
                base = base + "/v1";
            }
            String target = base + "/responses";
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body == null ? Map.of() : body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new UpstreamResponse(response.statusCode(), response.body());
        } catch (Exception ex) {
            return new UpstreamResponse(502, "{\"error\":\"upstream_failed\",\"message\":\""
                    + (ex.getMessage() == null ? "" : ex.getMessage().replace("\"", "'"))
                    + "\"}");
        }
    }
}
