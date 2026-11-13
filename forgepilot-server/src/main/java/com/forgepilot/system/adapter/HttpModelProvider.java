package com.forgepilot.system.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.model.UpstreamWireApi;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import com.forgepilot.system.port.ModelProviderPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpModelProvider implements ModelProviderPort {

    private final ModelProviderMapper modelProviderMapper;
    private final IntegrationSecretMapper integrationSecretMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpModelProvider(
            ModelProviderMapper modelProviderMapper,
            IntegrationSecretMapper integrationSecretMapper,
            ObjectMapper objectMapper) {
        this.modelProviderMapper = modelProviderMapper;
        this.integrationSecretMapper = integrationSecretMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public ModelProviderView get(String providerId) {
        ModelProvider provider = modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型 Provider 不存在");
        }
        return new ModelProviderView(
                provider.getId(),
                provider.getName(),
                provider.getProviderType(),
                provider.getBaseUrl(),
                provider.getUpstreamWireApi(),
                provider.getTranslationGateway() != null && !provider.getTranslationGateway().isBlank(),
                Boolean.TRUE.equals(provider.getEnabled()),
                false);
    }

    @Override
    public String complete(PlatformModelRequest request) {
        if (request == null || request.providerId() == null || request.providerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置模型 Provider，无法生成");
        }
        ModelProvider provider = modelProviderMapper.selectById(request.providerId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型 Provider 不可用，无法生成");
        }
        if (provider.getSecretId() == null || provider.getSecretId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置 API Key，无法生成");
        }
        IntegrationSecret secret = integrationSecretMapper.selectById(provider.getSecretId());
        if (secret == null || secret.getCiphertext() == null || secret.getCiphertext().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置 API Key，无法生成");
        }
        String apiKey = new String(secret.getCiphertext(), StandardCharsets.UTF_8);
        String modelId = request.modelId();
        if (modelId == null || modelId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置模型，无法生成");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成提示不能为空");
        }
        UpstreamWireApi wire = UpstreamWireApi.parse(provider.getUpstreamWireApi());
        try {
            return call(provider.getBaseUrl(), wire, modelId, request.prompt(), apiKey);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, UpstreamCallErrors.modelCallFailed(provider.getBaseUrl(), ex));
        }
    }

    private String call(String baseUrl, UpstreamWireApi wire, String modelId, String prompt, String apiKey)
            throws Exception {
        String root = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (root.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型 Provider 未配置接口地址");
        }
        String path;
        Map<String, Object> body;
        if (wire == UpstreamWireApi.RESPONSES) {
            path = root + "/responses";
            body = Map.of("model", modelId, "input", prompt);
        } else {
            path = root + "/chat/completions";
            body = Map.of(
                    "model",
                    modelId,
                    "messages",
                    List.of(Map.of("role", "user", "content", prompt)),
                    "temperature",
                    0.3);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String payload = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "模型接口返回 HTTP " + response.statusCode() + "：" + truncate(strip(payload, apiKey)));
        }
        String text = extractText(payload);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型接口没有返回可用文本");
        }
        return text.trim();
    }

    static String extractText(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(payload);
        if (root.hasNonNull("output_text")) {
            return root.get("output_text").asText("");
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            if (message.hasNonNull("content")) {
                return message.get("content").asText("");
            }
            if (choices.get(0).hasNonNull("text")) {
                return choices.get(0).get("text").asText("");
            }
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        if (part.hasNonNull("text")) {
                            if (!text.isEmpty()) {
                                text.append("\n");
                            }
                            text.append(part.get("text").asText(""));
                        }
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        return "";
    }

    private static String strip(String body, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || body == null) {
            return body == null ? "" : body;
        }
        return body.replace(apiKey, "***");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}
