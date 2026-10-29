package com.archanalyzer.optimization;

import java.util.*;

/**
 * PART 17: explainable weighted multi-objective scoring. Lower composite
 * score = better candidate (it is a "cost", mirroring dependencyScore's
 * "higher = worse" convention). Weights are named fields, not magic numbers
 * buried in an expression, so a candidate's ranking can be explained
 * factor-by-factor (PART 17: "clearly show why Candidate B ranked above
 * Candidate A").
 */
public class ScoringEngine {

    public double weightDependencyReduction = 0.35;
    public double weightCycleReduction = 0.25;
    public double weightRefactoringCost = 0.15;
    public double weightFilesChanged = 0.15;
    public double weightMaintainability = 0.10;

    public static class ScoreBreakdown {
        public double dependencyTerm, cycleTerm, costTerm, filesTerm, maintainabilityTerm, total;
    }

    public ScoreBreakdown score(ArchitectureCandidate candidate, double originalDependencyScore, int originalCycles) {
        ScoreBreakdown b = new ScoreBreakdown();
        double dependencyReduction = Math.max(0, originalDependencyScore - candidate.projectedDependencyScore);
        double cycleReduction = Math.max(0, originalCycles - candidate.projectedCycles);

        b.dependencyTerm = weightDependencyReduction * dependencyReduction;
        b.cycleTerm = weightCycleReduction * (cycleReduction * 20); // scale cycles to a comparable 0-100-ish range
        b.costTerm = -weightRefactoringCost * candidate.estimatedRefactoringCostHours;
        b.filesTerm = -weightFilesChanged * candidate.filesChanged;
        b.maintainabilityTerm = weightMaintainability * candidate.projectedArchitectureHealth / 2.0;

        b.total = b.dependencyTerm + b.cycleTerm + b.costTerm + b.filesTerm + b.maintainabilityTerm;
        candidate.compositeScore = Math.round(b.total * 100) / 100.0;
        return b;
    }

    /** Ranks candidates that satisfy the given constraints, best first. Constraint-violating candidates are excluded. */
    public List<ArchitectureCandidate> rankValid(List<ArchitectureCandidate> candidates, ConstraintSet constraints,
                                                   double originalDependencyScore, int originalCycles) {
        List<ArchitectureCandidate> valid = new ArrayList<>();
        for (ArchitectureCandidate c : candidates) {
            if (!constraints.isSatisfiedBy(c)) {
                c.status = "REJECTED";
                c.rejectionReason = "Violates user constraints (max files changed / external dependency / public API rules).";
                continue;
            }
            score(c, originalDependencyScore, originalCycles);
            valid.add(c);
        }
        valid.sort((a, b) -> Double.compare(b.compositeScore, a.compositeScore)); // descending: best first
        return valid;
    }
}
