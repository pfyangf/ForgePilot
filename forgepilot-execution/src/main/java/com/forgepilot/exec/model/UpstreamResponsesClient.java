package com.forgepilot.exec.model;

import java.util.Map;

public interface UpstreamResponsesClient {

    UpstreamResponse postResponses(String baseUrl, String apiKey, Map<String, Object> body);

    record UpstreamResponse(int statusCode, String body) {
    }
}
