package com.archanalyzer.simulator;

import java.util.*;

/**
 * Structured internal representation for every proposed architecture change.
 * Encapsulates the user's natural language request, resolved component target,
 * structured operations, assumptions, confidence, and validation plan.
 */
public class ChangeRequest {

    private final String requestId;
    private final String projectId;
    private String targetComponentId;
    private String secondaryComponentId;
    private String newComponentId;
    private ChangeOperationType operation;
    private String rawDescription;
    private String structuredSummary;
    private final List<String> affectedFiles = new ArrayList<>();
    private final List<String> affectedComponents = new ArrayList<>();
    private final List<String> proposedOperations = new ArrayList<>();
    private final List<String> assumptions = new ArrayList<>();
    private final List<String> unresolvedQuestions = new ArrayList<>();
    private final List<String> validationPlan = new ArrayList<>();
    private double confidence = 1.0;
    private boolean ambiguous = false;
    private final List<String> candidateMatches = new ArrayList<>();
    private final long creationTimestamp;

    public ChangeRequest(String requestId, String projectId, String rawDescription) {
        this.requestId = requestId;
        this.projectId = projectId;
        this.rawDescription = rawDescription;
        this.creationTimestamp = System.currentTimeMillis();
    }

    public String getRequestId() { return requestId; }
    public String getProjectId() { return projectId; }

    public String getTargetComponentId() { return targetComponentId; }
    public void setTargetComponentId(String targetComponentId) { this.targetComponentId = targetComponentId; }

    public String getSecondaryComponentId() { return secondaryComponentId; }
    public void setSecondaryComponentId(String secondaryComponentId) { this.secondaryComponentId = secondaryComponentId; }

    public String getNewComponentId() { return newComponentId; }
    public void setNewComponentId(String newComponentId) { this.newComponentId = newComponentId; }

    public ChangeOperationType getOperation() { return operation; }
    public void setOperation(ChangeOperationType operation) { this.operation = operation; }

    public String getRawDescription() { return rawDescription; }
    public void setRawDescription(String rawDescription) { this.rawDescription = rawDescription; }

    public String getStructuredSummary() { return structuredSummary; }
    public void setStructuredSummary(String structuredSummary) { this.structuredSummary = structuredSummary; }

    public List<String> getAffectedFiles() { return affectedFiles; }
    public List<String> getAffectedComponents() { return affectedComponents; }
    public List<String> getProposedOperations() { return proposedOperations; }
    public List<String> getAssumptions() { return assumptions; }
    public List<String> getUnresolvedQuestions() { return unresolvedQuestions; }
    public List<String> getValidationPlan() { return validationPlan; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public boolean isAmbiguous() { return ambiguous; }
    public void setAmbiguous(boolean ambiguous) { this.ambiguous = ambiguous; }

    public List<String> getCandidateMatches() { return candidateMatches; }
    public long getCreationTimestamp() { return creationTimestamp; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestId", requestId);
        map.put("projectId", projectId);
        map.put("targetComponentId", targetComponentId);
        map.put("secondaryComponentId", secondaryComponentId);
        map.put("newComponentId", newComponentId);
        map.put("operation", operation != null ? operation.name() : "OTHER");
        map.put("rawDescription", rawDescription);
        map.put("structuredSummary", structuredSummary);
        map.put("confidence", confidence);
        map.put("ambiguous", ambiguous);
        map.put("candidateMatches", candidateMatches);
        map.put("affectedFiles", affectedFiles);
        map.put("affectedComponents", affectedComponents);
        map.put("proposedOperations", proposedOperations);
        map.put("assumptions", assumptions);
        map.put("unresolvedQuestions", unresolvedQuestions);
        map.put("validationPlan", validationPlan);
        map.put("creationTimestamp", creationTimestamp);
        return map;
    }
}
