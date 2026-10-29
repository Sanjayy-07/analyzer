package com.archanalyzer.ai;

/**
 * Common abstraction for LLM providers (Gemini, OpenAI, Anthropic, Ollama, Mock).
 */
public interface LlmProvider {

    /**
     * Sends the system and user prompts to the LLM and returns the raw response text.
     */
    String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException;

    String getProviderName();

    String getModelName();
}
