package com.archanalyzer.optimization;

import java.util.ArrayList;
import java.util.List;

/** PART 15: one proposed alternative architecture, with projected metrics. */
public class ArchitectureCandidate {
    public String id;
    public String strategyName;          // e.g. "Dependency Inversion", "Interface Extraction"
    public String targetComponentId;
    public String rationale;

    public double projectedDependencyScore;
    public double projectedCoupling;
    public int projectedCycles;
    public double projectedArchitectureHealth;
    public String projectedMlRisk;

    public int filesChanged;
    public boolean introducesExternalDependency = false;
    public boolean preservesPublicApi = true;
    public int estimatedRefactoringCostHours;

    public double compositeScore; // filled in by ScoringEngine
    public String status = "PROPOSED"; // PROPOSED, REJECTED, ACCEPTED, VALIDATED_PASS, VALIDATED_FAIL
    public String rejectionReason;

    public final List<String> plannedChanges = new ArrayList<>();

    @Override
    public String toString() {
        return String.format("Candidate[%s strategy=%s target=%s depScore=%.1f health=%.1f cycles=%d score=%.2f status=%s]",
            id, strategyName, targetComponentId, projectedDependencyScore, projectedArchitectureHealth,
            projectedCycles, compositeScore, status);
    }
}
