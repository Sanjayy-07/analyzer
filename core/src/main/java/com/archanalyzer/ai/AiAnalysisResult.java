package com.archanalyzer.ai;

import java.util.Collections;
import java.util.List;

/**
 * Result of the Generative AI architecture analysis.
 */
public class AiAnalysisResult {

    public enum Status {
        LIVE_SUCCESS,
        UNCONFIGURED,
        DISABLED,
        FAILED
    }

    private final Status status;
    private final String provider;
    private final String model;
    private final String message;
    private final List<ArchitectureSmellExplanation> explanations;
    private final List<AiRefactoringRecommendation> recommendations;

    public AiAnalysisResult(Status status, String provider, String model, String message,
                            List<ArchitectureSmellExplanation> explanations,
                            List<AiRefactoringRecommendation> recommendations) {
        this.status = status;
        this.provider = provider != null ? provider : "none";
        this.model = model != null ? model : "none";
        this.message = message != null ? message : "";
        this.explanations = explanations != null ? explanations : List.of();
        this.recommendations = recommendations != null ? recommendations : List.of();
    }

    public static AiAnalysisResult unconfigured(String message) {
        return new AiAnalysisResult(Status.UNCONFIGURED, "none", "none", message, List.of(), List.of());
    }

    public static AiAnalysisResult disabled(String message) {
        return new AiAnalysisResult(Status.DISABLED, "none", "none", message, List.of(), List.of());
    }

    public static AiAnalysisResult failed(String provider, String model, String errorMessage) {
        return new AiAnalysisResult(Status.FAILED, provider, model, errorMessage, List.of(), List.of());
    }

    public static AiAnalysisResult success(String provider, String model,
                                          List<ArchitectureSmellExplanation> explanations,
                                          List<AiRefactoringRecommendation> recommendations) {
        return new AiAnalysisResult(Status.LIVE_SUCCESS, provider, model, "Success", explanations, recommendations);
    }

    public Status getStatus() { return status; }
    public boolean isAvailable() { return status == Status.LIVE_SUCCESS; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return message; }
    public List<ArchitectureSmellExplanation> getExplanations() { return Collections.unmodifiableList(explanations); }
    public List<AiRefactoringRecommendation> getRecommendations() { return Collections.unmodifiableList(recommendations); }
}
