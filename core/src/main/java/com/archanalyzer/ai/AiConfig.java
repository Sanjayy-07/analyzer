package com.archanalyzer.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration for the Generative AI explanation and recommendation gateway.
 *
 * Automatically loads from environment variables, system properties, or root .env file:
 *  - LLM_PROVIDER: "gemini", "openai", "anthropic", "ollama", or "disabled"
 *  - LLM_API_KEY: Provider API key
 *  - LLM_API_BASE_URL: Custom endpoint URL (e.g. http://localhost:11434 for Ollama)
 *  - LLM_MODEL: Model name (defaults to gemini-2.5-flash / gemini-3.8-flash for Gemini)
 *  - LLM_TIMEOUT_SECONDS: Request timeout in seconds (default: 30)
 */
public class AiConfig {

    private static final Map<String, String> DOTENV_VALUES = loadDotenv();

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public AiConfig(String provider, String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this.provider = provider != null ? provider.trim().toLowerCase() : "disabled";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        String resolvedModel = model != null && !model.isBlank() ? model.trim() : defaultModelFor(this.provider);
        if (isRetiredGeminiModel(resolvedModel)) {
            resolvedModel = "gemini-3.5-flash";
        }
        this.model = resolvedModel;
        this.timeoutSeconds = timeoutSeconds > 0 ? Math.max(timeoutSeconds, 60) : 90;
    }

    public static AiConfig loadFromEnvironment() {
        String provider = getEnvOrProp("LLM_PROVIDER", "");
        String apiKey = getEnvOrProp("LLM_API_KEY", "");
        String baseUrl = getEnvOrProp("LLM_API_BASE_URL", "");
        String model = getEnvOrProp("LLM_MODEL", "");
        String timeoutStr = getEnvOrProp("LLM_TIMEOUT_SECONDS", "90");

        int timeout = 90;
        try {
            timeout = Math.max(Integer.parseInt(timeoutStr), 60);
        } catch (NumberFormatException ignored) {}

        // If provider is not set, infer from existing keys if present
        if (provider.isBlank()) {
            if (!apiKey.isBlank() && !isPlaceholder(apiKey)) {
                if (apiKey.startsWith("sk-ant-")) provider = "anthropic";
                else if (apiKey.startsWith("AIza") || apiKey.startsWith("AQ.A")) provider = "gemini";
                else if (apiKey.startsWith("sk-")) provider = "openai";
            } else if (!baseUrl.isBlank() && baseUrl.contains("11434")) {
                provider = "ollama";
            } else {
                provider = "disabled";
            }
        }

        if (model.isBlank() || isRetiredGeminiModel(model)) {
            model = defaultModelFor(provider);
        }

        return new AiConfig(provider, apiKey, baseUrl, model, timeout);
    }

    private static boolean isRetiredGeminiModel(String modelName) {
        if (modelName == null) return false;
        String m = modelName.trim().toLowerCase();
        return m.contains("1.5") || m.contains("2.0") || m.contains("2.5") || m.equals("gemini-pro");
    }

    public static AiConfig disabled() {
        return new AiConfig("disabled", "", "", "", 90);
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
            case "gemini" -> "gemini-3.5-flash";
            case "openai" -> "gpt-4o-mini";
            case "anthropic" -> "claude-3-5-sonnet-20241022";
            case "ollama" -> "llama3";
            default -> "default-model";
        };
    }

    private static String getEnvOrProp(String name, String fallback) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) return val.trim();
        val = System.getProperty(name);
        if (val != null && !val.isBlank()) return val.trim();
        val = DOTENV_VALUES.get(name);
        if (val != null && !val.isBlank()) return val.trim();
        return fallback;
    }

    private static Map<String, String> loadDotenv() {
        Map<String, String> map = new LinkedHashMap<>();
        List<Path> envCandidates = new ArrayList<>();
        List<Path> exampleCandidates = new ArrayList<>();

        Path current = Path.of("").toAbsolutePath();
        Path p = current;
        for (int i = 0; i < 4 && p != null; i++) {
            envCandidates.add(p.resolve(".env"));
            exampleCandidates.add(p.resolve(".env.example"));
            p = p.getParent();
        }

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            Path u = Path.of(userDir).toAbsolutePath();
            envCandidates.add(u.resolve(".env"));
            exampleCandidates.add(u.resolve(".env.example"));
            if (u.getParent() != null) {
                envCandidates.add(u.getParent().resolve(".env"));
                exampleCandidates.add(u.getParent().resolve(".env.example"));
            }
        }

        // Pass 1: Parse real .env files first
        for (Path candidate : envCandidates) {
            parseEnvFileInto(candidate, map);
        }

        // Pass 2: Fall back to .env.example only if no real keys were found
        if (map.isEmpty()) {
            for (Path candidate : exampleCandidates) {
                parseEnvFileInto(candidate, map);
            }
        }

        return map;
    }

    private static void parseEnvFileInto(Path candidate, Map<String, String> map) {
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
            try {
                List<String> lines = Files.readAllLines(candidate);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                            v = v.substring(1, v.length() - 1);
                        }
                        if (!k.isEmpty() && !map.containsKey(k) && !v.isEmpty() && !isPlaceholder(v)) {
                            map.put(k, v);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public String getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
