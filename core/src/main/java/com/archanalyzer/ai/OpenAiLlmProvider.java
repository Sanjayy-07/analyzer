package com.archanalyzer.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider implementation for OpenAI and OpenAI-compatible APIs (Groq, DeepSeek, LocalAI, Ollama /v1).
 */
public class OpenAiLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OpenAiLlmProvider(AiConfig config) {
        this.apiKey = config.getApiKey();
        String url = config.getBaseUrl();
        if (url == null || url.isBlank()) {
            this.baseUrl = "https://api.openai.com";
        } else {
            this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        this.model = config.getModel();
        this.timeout = Duration.ofSeconds(config.getTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .build();
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException {
        String endpoint = baseUrl.endsWith("/v1") ? baseUrl + "/chat/completions" : baseUrl + "/v1/chat/completions";

        String jsonPayload = """
            {
              "model": "%s",
              "messages": [
                {"role": "system", "content": "%s"},
                {"role": "user", "content": "%s"}
              ],
              "temperature": 0.2,
              "response_format": {"type": "json_object"}
            }
            """.formatted(escapeJson(model), escapeJson(systemPrompt), escapeJson(userPrompt));

        try {
            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (!apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AiServiceException("OpenAI API returned HTTP " + response.statusCode() + ": " + sanitize(response.body()));
            }

            return extractContent(response.body());
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    private String extractContent(String responseBody) throws AiServiceException {
        // Robust pure-JDK extraction of content from {"choices": [{"message": {"content": "..."}}]}
        int contentIdx = responseBody.indexOf("\"content\":");
        if (contentIdx < 0) {
            throw new AiServiceException("Unexpected API response structure (no content field): " + sanitize(responseBody));
        }
        int quoteStart = responseBody.indexOf('"', contentIdx + 10);
        if (quoteStart < 0) {
            throw new AiServiceException("Malformed JSON string in content field");
        }

        // Scan unescaped closing quote
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = quoteStart + 1; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (i + 4 < responseBody.length()) {
                            String hex = responseBody.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                sb.append(c);
                            }
                        }
                    }
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break; // Found closing quote
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String sanitize(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    @Override public String getProviderName() { return "OpenAI"; }
    @Override public String getModelName() { return model; }
}
