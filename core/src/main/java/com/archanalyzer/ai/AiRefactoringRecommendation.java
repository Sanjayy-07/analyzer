package com.archanalyzer.ai;

/**
 * AI-generated refactoring recommendation based on measured architecture findings.
 */
public class AiRefactoringRecommendation {

    private final String id;
    private final String targetComponentId;
    private final String detectedProblem;
    private final String proposedRefactoring;
    private final String whyItHelps;
    private final String expectedBenefits;
    private final String potentialRisks;
    private final String preconditions;
    private final boolean engineSupported;
    private final String implementationPlan;
    private final String validationRequirements;

    public AiRefactoringRecommendation(String id, String targetComponentId, String detectedProblem,
                                       String proposedRefactoring, String whyItHelps,
                                       String expectedBenefits, String potentialRisks,
                                       String preconditions, boolean engineSupported,
                                       String implementationPlan, String validationRequirements) {
        this.id = id;
        this.targetComponentId = targetComponentId;
        this.detectedProblem = detectedProblem;
        this.proposedRefactoring = proposedRefactoring;
        this.whyItHelps = whyItHelps;
        this.expectedBenefits = expectedBenefits;
        this.potentialRisks = potentialRisks;
        this.preconditions = preconditions;
        this.engineSupported = engineSupported;
        this.implementationPlan = implementationPlan;
        this.validationRequirements = validationRequirements;
    }

    public String getId() { return id; }
    public String getTargetComponentId() { return targetComponentId; }
    public String getDetectedProblem() { return detectedProblem; }
    public String getProposedRefactoring() { return proposedRefactoring; }
    public String getWhyItHelps() { return whyItHelps; }
    public String getExpectedBenefits() { return expectedBenefits; }
    public String getPotentialRisks() { return potentialRisks; }
    public String getPreconditions() { return preconditions; }
    public boolean isEngineSupported() { return engineSupported; }
    public String getImplementationPlan() { return implementationPlan; }
    public String getValidationRequirements() { return validationRequirements; }
}
