package com.archanalyzer.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider implementation for Anthropic Claude API (/v1/messages).
 */
public class AnthropicLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public AnthropicLlmProvider(AiConfig config) {
        this.apiKey = config.getApiKey();
        String url = config.getBaseUrl();
        this.baseUrl = (url == null || url.isBlank()) ? "https://api.anthropic.com" : url;
        this.model = config.getModel();
        this.timeout = Duration.ofSeconds(config.getTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .build();
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException {
        String endpoint = baseUrl + "/v1/messages";

        String jsonPayload = """
            {
              "model": "%s",
              "system": "%s",
              "messages": [
                {"role": "user", "content": "%s"}
              ],
              "max_tokens": 4096,
              "temperature": 0.2
            }
            """.formatted(escapeJson(model), escapeJson(systemPrompt), escapeJson(userPrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AiServiceException("Anthropic API returned HTTP " + response.statusCode() + ": " + sanitize(response.body()));
            }

            return extractContent(response.body());
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    private String extractContent(String responseBody) throws AiServiceException {
        int textIdx = responseBody.indexOf("\"text\":");
        if (textIdx < 0) {
            throw new AiServiceException("Unexpected Anthropic response (no text part found): " + sanitize(responseBody));
        }
        int quoteStart = responseBody.indexOf('"', textIdx + 7);
        if (quoteStart < 0) {
            throw new AiServiceException("Malformed text part in Anthropic response");
        }

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
                break;
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

    @Override public String getProviderName() { return "Anthropic"; }
    @Override public String getModelName() { return model; }
}
