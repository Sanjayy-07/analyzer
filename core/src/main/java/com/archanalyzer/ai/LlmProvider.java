package com.archanalyzer.ai;

/**
 * Common abstraction for LLM providers (Gemini, OpenAI, Anthropic, Ollama, Mock).
 */
public interface LlmProvider {

    /**
     * Sends the system and user prompts to the LLM and returns the raw response text.
     */
    String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException;

    /**
     * Sends the system prompt, prior conversation turns, and current user question to the LLM.
     * Default implementation delegates to generateCompletion if multi-turn is not customized.
     */
    default String generateChatCompletion(String systemPrompt, java.util.List<ChatMessage> conversationHistory, String userPrompt) throws AiServiceException {
        return generateCompletion(systemPrompt, userPrompt);
    }

    String getProviderName();

    String getModelName();
}
