package com.archanalyzer.ai;

import java.util.function.Function;

/**
 * Test double for deterministic testing of the AI integration without network calls.
 */
public class MockLlmProvider implements LlmProvider {

    private final String providerName;
    private final String modelName;
    private Function<String, String> responder;
    private boolean shouldThrow = false;
    private String errorMessage = "Simulated provider error";

    public MockLlmProvider() {
        this("MockProvider", "mock-model");
    }

    public MockLlmProvider(String providerName, String modelName) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.responder = prompt -> defaultResponse();
    }

    public void setCustomResponder(Function<String, String> responder) {
        this.responder = responder;
    }

    public void setSimulateFailure(boolean shouldThrow, String errorMessage) {
        this.shouldThrow = shouldThrow;
        this.errorMessage = errorMessage;
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) throws AiServiceException {
        if (shouldThrow) {
            throw new AiServiceException(errorMessage);
        }
        return responder.apply(userPrompt);
    }

    private String defaultResponse() {
        return """
            {
              "explanations": [],
              "recommendations": []
            }
            """;
    }

    @Override public String getProviderName() { return providerName; }
    @Override public String getModelName() { return modelName; }
}
