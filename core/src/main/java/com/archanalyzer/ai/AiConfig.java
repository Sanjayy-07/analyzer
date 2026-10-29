package com.archanalyzer.ai;

/**
 * Configuration for the Generative AI explanation and recommendation gateway.
 *
 * Configurable via environment variables (or system properties):
 *  - LLM_PROVIDER: "gemini", "openai", "anthropic", "ollama", or "disabled"
 *  - LLM_API_KEY: Provider API key (never hardcoded, never committed)
 *  - LLM_API_BASE_URL: Custom endpoint URL (e.g. http://localhost:11434 for Ollama)
 *  - LLM_MODEL: Model name (e.g. gemini-1.5-flash, gpt-4o-mini, claude-3-5-sonnet, llama3)
 *  - LLM_TIMEOUT_SECONDS: Request timeout in seconds (default: 30)
 */
public class AiConfig {

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public AiConfig(String provider, String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this.provider = provider != null ? provider.trim().toLowerCase() : "disabled";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : defaultModelFor(this.provider);
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    public static AiConfig loadFromEnvironment() {
        String provider = getEnvOrProp("LLM_PROVIDER", "");
        String apiKey = getEnvOrProp("LLM_API_KEY", "");
        String baseUrl = getEnvOrProp("LLM_API_BASE_URL", "");
        String model = getEnvOrProp("LLM_MODEL", "");
        String timeoutStr = getEnvOrProp("LLM_TIMEOUT_SECONDS", "30");

        int timeout = 30;
        try {
            timeout = Integer.parseInt(timeoutStr);
        } catch (NumberFormatException ignored) {}

        // If provider is not set, infer from existing keys if present
        if (provider.isBlank()) {
            if (!apiKey.isBlank() && !isPlaceholder(apiKey)) {
                if (apiKey.startsWith("sk-ant-")) provider = "anthropic";
                else if (apiKey.startsWith("AIza")) provider = "gemini";
                else if (apiKey.startsWith("sk-")) provider = "openai";
            } else if (!baseUrl.isBlank() && baseUrl.contains("11434")) {
                provider = "ollama";
            } else {
                provider = "disabled";
            }
        }

        return new AiConfig(provider, apiKey, baseUrl, model, timeout);
    }

    public static AiConfig disabled() {
        return new AiConfig("disabled", "", "", "", 30);
    }

    public boolean isConfigured() {
        if ("disabled".equalsIgnoreCase(provider) || provider.isBlank()) return false;
        if ("ollama".equalsIgnoreCase(provider)) return true; // Ollama runs locally without mandatory key
        return !apiKey.isBlank() && !isPlaceholder(apiKey);
    }

    private static boolean isPlaceholder(String key) {
        String lower = key.toLowerCase();
        return lower.contains("replace") || lower.contains("placeholder") || lower.contains("changeme");
    }

    private static String defaultModelFor(String provider) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> "gemini-1.5-flash";
            case "openai" -> "gpt-4o-mini";
            case "anthropic" -> "claude-3-5-sonnet-20241022";
            case "ollama" -> "llama3";
            default -> "default-model";
        };
    }

    private static String getEnvOrProp(String name, String fallback) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(name);
        if (val != null && !val.isBlank()) return val;
        return fallback;
    }

    public String getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
