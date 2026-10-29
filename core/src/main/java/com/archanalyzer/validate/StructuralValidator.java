package com.archanalyzer.validate;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;

/**
 * PART 20/22: compares the re-analyzed post-refactor metrics against the
 * captured Baseline. A pure diff — every number here is measured, not
 * asserted.
 */
public class StructuralValidator {

    public static class ComparisonResult {
        public double architectureHealthBefore, architectureHealthAfter, architectureHealthDelta;
        public double dependencyScoreBefore, dependencyScoreAfter;
        public double dependencyReductionPercent;
        public int cyclesBefore, cyclesAfter;
        public double cycleReductionPercent;
        public boolean improved;
        public boolean regressed;
        public java.util.List<String> issues = new java.util.ArrayList<>();
    }

    public ComparisonResult compare(Baseline baseline, ProjectMetrics after) {
        return compare(baseline, after, null);
    }

    public ComparisonResult compare(Baseline baseline, ProjectMetrics after, com.archanalyzer.model.ProjectModel modelAfter) {
        ComparisonResult r = new ComparisonResult();
        r.architectureHealthBefore = baseline.architectureHealth;
        r.architectureHealthAfter = after.architectureHealthScore;
        r.architectureHealthDelta = round1(after.architectureHealthScore - baseline.architectureHealth);

        r.dependencyScoreBefore = baseline.dependencyScore;
        r.dependencyScoreAfter = after.dependencyScore;
        r.dependencyReductionPercent = baseline.dependencyScore == 0 ? 0 :
            round1(100 * (baseline.dependencyScore - after.dependencyScore) / baseline.dependencyScore);

        r.cyclesBefore = baseline.cycleCount;
        r.cyclesAfter = after.cycleCount;
        r.cycleReductionPercent = baseline.cycleCount == 0 ? 0 :
            round1(100.0 * (baseline.cycleCount - after.cycleCount) / baseline.cycleCount);

        r.improved = r.architectureHealthDelta > 0 && after.dependencyScore <= baseline.dependencyScore;
        r.regressed = r.architectureHealthDelta < 0 || after.dependencyScore > baseline.dependencyScore
            || after.cycleCount > baseline.cycleCount;

        if (modelAfter != null && modelAfter.hasParseErrors()) {
            r.regressed = true;
            r.improved = false;
            r.issues.add("Post-refactoring parse errors detected in " + modelAfter.getParseErrors().size() + " file(s).");
        }

        return r;
    }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
