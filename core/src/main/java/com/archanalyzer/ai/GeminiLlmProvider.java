package com.archanalyzer.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider implementation for Google Gemini API (generateContent).
 */
public class GeminiLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public GeminiLlmProvider(AiConfig config) {
        this.apiKey = config.getApiKey();
        String url = config.getBaseUrl();
        this.baseUrl = (url == null || url.isBlank()) ? "https://generativelanguage.googleapis.com" : url;
        this.model = config.getModel();
        this.timeout = Duration.ofSeconds(Math.max(config.getTimeoutSeconds(), 90));
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException {
        String jsonPayload = """
            {
              "systemInstruction": {
                "parts": [{"text": "%s"}]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [{"text": "%s"}]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.2
              }
            }
            """.formatted(escapeJson(systemPrompt), escapeJson(userPrompt));

        return executeWithModels(jsonPayload);
    }

    @Override
    public String generateChatCompletion(String systemPrompt, java.util.List<ChatMessage> conversationHistory, String userPrompt) throws AiServiceException {
        StringBuilder contentsJson = new StringBuilder();
        contentsJson.append("[\n");

        boolean hasEntries = false;
        if (conversationHistory != null) {
            for (ChatMessage msg : conversationHistory) {
                if (msg == null || msg.content() == null || msg.content().isBlank()) continue;
                String role = "assistant".equalsIgnoreCase(msg.role()) || "model".equalsIgnoreCase(msg.role()) ? "model" : "user";
                if (hasEntries) contentsJson.append(",\n");
                contentsJson.append("    {\n");
                contentsJson.append("      \"role\": \"").append(role).append("\",\n");
                contentsJson.append("      \"parts\": [{\"text\": \"").append(escapeJson(msg.content())).append("\"}]\n");
                contentsJson.append("    }");
                hasEntries = true;
            }
        }

        // Add current user prompt
        if (hasEntries) contentsJson.append(",\n");
        contentsJson.append("    {\n");
        contentsJson.append("      \"role\": \"user\",\n");
        contentsJson.append("      \"parts\": [{\"text\": \"").append(escapeJson(userPrompt)).append("\"}]\n");
        contentsJson.append("    }\n  ]");

        String jsonPayload = """
            {
              "systemInstruction": {
                "parts": [{"text": "%s"}]
              },
              "contents": %s,
              "generationConfig": {
                "temperature": 0.3
              }
            }
            """.formatted(escapeJson(systemPrompt), contentsJson.toString());

        return executeWithModels(jsonPayload);
    }

    private String executeWithModels(String jsonPayload) throws AiServiceException {
        java.util.List<String> modelsToTry = new java.util.ArrayList<>();
        if (model != null && !model.isBlank() && !isRetired(model)) {
            modelsToTry.add(model);
        }
        for (String m : java.util.List.of("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-3.8-flash")) {
            if (!modelsToTry.contains(m)) modelsToTry.add(m);
        }

        AiServiceException lastEx = null;
        for (String targetModel : modelsToTry) {
            String endpoint = baseUrl + "/v1beta/models/" + targetModel + ":generateContent?key=" + apiKey;
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        return extractContent(response.body());
                    } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                        throw new AiServiceException("Gemini authentication failed (HTTP " + response.statusCode() + "): Invalid or unauthorized API key.");
                    } else if (response.statusCode() == 404) {
                        lastEx = new AiServiceException("Gemini model " + targetModel + " not found (HTTP 404): " + sanitize(response.body()));
                        break; // 404 is model-specific, move to next model immediately
                    } else if (response.statusCode() == 429) {
                        lastEx = new AiServiceException("Gemini quota or rate limit exceeded (HTTP 429): " + sanitize(response.body()));
                        break; // Rate limit reached on this model, attempt fallback model
                    } else if (response.statusCode() == 503) {
                        lastEx = new AiServiceException("Gemini model " + targetModel + " unavailable (HTTP 503): " + sanitize(response.body()));
                        if (attempt < maxRetries) {
                            long waitMs = 1500L * attempt;
                            java.util.Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                            if (retryAfter.isPresent()) {
                                try {
                                    waitMs = Math.min(Long.parseLong(retryAfter.get()) * 1000L, 5000L);
                                } catch (NumberFormatException ignored) {}
                            }
                            try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            continue;
                        }
                    } else {
                        lastEx = new AiServiceException("Gemini API error (HTTP " + response.statusCode() + "): " + sanitize(response.body()));
                        break;
                    }
                } catch (java.net.http.HttpTimeoutException te) {
                    lastEx = new AiServiceException("Gemini model " + targetModel + " request timed out (" + te.getMessage() + ")");
                    break;
                } catch (AiServiceException e) {
                    if (e.getMessage() != null && e.getMessage().contains("authentication failed")) {
                        throw e; // Fail fast on authentication errors
                    }
                    lastEx = e;
                    break;
                } catch (Exception e) {
                    lastEx = new AiServiceException("Network error connecting to Gemini API (" + targetModel + "): " + e.getMessage(), e);
                    if (attempt < maxRetries) {
                        try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                }
            }
        }

        throw lastEx != null ? lastEx : new AiServiceException("Unable to connect to Gemini API");
    }

    private static boolean isRetired(String m) {
        if (m == null) return false;
        String lower = m.toLowerCase();
        return lower.contains("1.5") || lower.contains("2.0") || lower.contains("2.5") || lower.equals("gemini-pro");
    }

    private String extractContent(String responseBody) throws AiServiceException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiServiceException("Empty response body returned by Gemini API");
        }
        int textIdx = responseBody.indexOf("\"text\":");
        if (textIdx < 0) {
            throw new AiServiceException("Unexpected Gemini response (no text part found): " + sanitize(responseBody));
        }
        int quoteStart = responseBody.indexOf('"', textIdx + 7);
        if (quoteStart < 0) {
            throw new AiServiceException("Malformed text part in Gemini response");
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
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new AiServiceException("Gemini returned empty text content");
        }
        return result;
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

    @Override public String getProviderName() { return "Google Gemini"; }
    @Override public String getModelName() { return model; }
}
