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
    private final String architecturePattern;
    private final String plainEnglishOverview;
    private final List<String> applicationFlowSteps;
    private final List<String> detectedTechStack;
    private final String maintainabilitySummary;

    public AiAnalysisResult(Status status, String provider, String model, String message,
                            List<ArchitectureSmellExplanation> explanations,
                            List<AiRefactoringRecommendation> recommendations) {
        this(status, provider, model, message, explanations, recommendations,
             "", "", List.of(), List.of(), "");
    }

    public AiAnalysisResult(Status status, String provider, String model, String message,
                            List<ArchitectureSmellExplanation> explanations,
                            List<AiRefactoringRecommendation> recommendations,
                            String architecturePattern,
                            String plainEnglishOverview,
                            List<String> applicationFlowSteps,
                            List<String> detectedTechStack,
                            String maintainabilitySummary) {
        this.status = status;
        this.provider = provider != null ? provider : "none";
        this.model = model != null ? model : "none";
        this.message = message != null ? message : "";
        this.explanations = explanations != null ? explanations : List.of();
        this.recommendations = recommendations != null ? recommendations : List.of();
        this.architecturePattern = architecturePattern != null ? architecturePattern : "";
        this.plainEnglishOverview = plainEnglishOverview != null ? plainEnglishOverview : "";
        this.applicationFlowSteps = applicationFlowSteps != null ? applicationFlowSteps : List.of();
        this.detectedTechStack = detectedTechStack != null ? detectedTechStack : List.of();
        this.maintainabilitySummary = maintainabilitySummary != null ? maintainabilitySummary : "";
    }

    public static AiAnalysisResult unconfigured(String message) {
        return new AiAnalysisResult(Status.UNCONFIGURED, "none", "none", message, List.of(), List.of());
    }

    public static AiAnalysisResult unconfigured(String message,
                                               String architecturePattern,
                                               String plainEnglishOverview,
                                               List<String> applicationFlowSteps,
                                               List<String> detectedTechStack,
                                               String maintainabilitySummary) {
        return new AiAnalysisResult(Status.UNCONFIGURED, "none", "none", message, List.of(), List.of(),
            architecturePattern, plainEnglishOverview, applicationFlowSteps, detectedTechStack, maintainabilitySummary);
    }

    public static AiAnalysisResult disabled(String message) {
        return new AiAnalysisResult(Status.DISABLED, "none", "none", message, List.of(), List.of());
    }

    public static AiAnalysisResult failed(String provider, String model, String errorMessage) {
        return new AiAnalysisResult(Status.FAILED, provider, model, errorMessage, List.of(), List.of());
    }

    public static AiAnalysisResult failed(String provider, String model, String errorMessage,
                                         String architecturePattern,
                                         String plainEnglishOverview,
                                         List<String> applicationFlowSteps,
                                         List<String> detectedTechStack,
                                         String maintainabilitySummary) {
        return new AiAnalysisResult(Status.FAILED, provider, model, errorMessage, List.of(), List.of(),
            architecturePattern, plainEnglishOverview, applicationFlowSteps, detectedTechStack, maintainabilitySummary);
    }

    public static AiAnalysisResult success(String provider, String model,
                                          List<ArchitectureSmellExplanation> explanations,
                                          List<AiRefactoringRecommendation> recommendations) {
        return new AiAnalysisResult(Status.LIVE_SUCCESS, provider, model, "Success", explanations, recommendations);
    }

    public static AiAnalysisResult success(String provider, String model,
                                          List<ArchitectureSmellExplanation> explanations,
                                          List<AiRefactoringRecommendation> recommendations,
                                          String architecturePattern,
                                          String plainEnglishOverview,
                                          List<String> applicationFlowSteps,
                                          List<String> detectedTechStack,
                                          String maintainabilitySummary) {
        return new AiAnalysisResult(Status.LIVE_SUCCESS, provider, model, "Success", explanations, recommendations,
            architecturePattern, plainEnglishOverview, applicationFlowSteps, detectedTechStack, maintainabilitySummary);
    }

    public Status getStatus() { return status; }
    public boolean isAvailable() { return status == Status.LIVE_SUCCESS; }
    public boolean hasOverview() { return plainEnglishOverview != null && !plainEnglishOverview.isBlank(); }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return message; }
    public List<ArchitectureSmellExplanation> getExplanations() { return Collections.unmodifiableList(explanations); }
    public List<AiRefactoringRecommendation> getRecommendations() { return Collections.unmodifiableList(recommendations); }
    public String getArchitecturePattern() { return architecturePattern; }
    public String getPlainEnglishOverview() { return plainEnglishOverview; }
    public List<String> getApplicationFlowSteps() { return Collections.unmodifiableList(applicationFlowSteps); }
    public List<String> getDetectedTechStack() { return Collections.unmodifiableList(detectedTechStack); }
    public String getMaintainabilitySummary() { return maintainabilitySummary; }
}
