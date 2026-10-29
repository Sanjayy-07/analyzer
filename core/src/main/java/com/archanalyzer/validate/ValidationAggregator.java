package com.archanalyzer.validate;

/**
 * PART 20: combines build status + structural comparison into the final
 * verdict shown to the user: PASS / WARNING / FAIL. Never claims that
 * compilation alone proves behavioral equivalence (PART 20's explicit
 * warning) — that claim is only ever "structurally validated", worded as such.
 */
public class ValidationAggregator {

    public enum Verdict { PASS, WARNING, FAIL }

    public static class ValidationResult {
        public Verdict verdict;
        public BuildValidator.BuildResult buildResult;
        public StructuralValidator.ComparisonResult structuralComparison;
        public String summary;
    }

    public ValidationResult aggregate(BuildValidator.BuildResult build, StructuralValidator.ComparisonResult structural) {
        ValidationResult result = new ValidationResult();
        result.buildResult = build;
        result.structuralComparison = structural;

        if (build.status == BuildValidator.Status.FAIL) {
            result.verdict = Verdict.FAIL;
            result.summary = "Build failed after refactoring (" + build.errors.size() + " compiler error(s)). " +
                "Candidate rejected; recovery/re-optimization should select the next-best candidate.";
        } else if (structural.regressed) {
            result.verdict = Verdict.FAIL;
            result.summary = "Build succeeded, but structural metrics regressed " +
                "(architecture health " + structural.architectureHealthDelta + ", " +
                "cycles " + structural.cyclesBefore + " -> " + structural.cyclesAfter + "). Candidate rejected.";
        } else if (build.status == BuildValidator.Status.SKIPPED) {
            result.verdict = Verdict.WARNING;
            result.summary = "Structural metrics improved, but the build step was SKIPPED " +
                "(no compiler/classpath available) — compilation was not actually verified. " +
                "Treat this result as provisional until a real build check runs.";
        } else if (!structural.improved) {
            result.verdict = Verdict.WARNING;
            result.summary = "Build succeeded and no regression was detected, but the improvement was not " +
                "clearly measurable against the baseline. Review manually before accepting.";
        } else {
            result.verdict = Verdict.PASS;
            result.summary = String.format(
                "Build succeeded. Dependency score improved by %.1f%%, cycles reduced by %.1f%%, " +
                "architecture health %+.1f.",
                structural.dependencyReductionPercent, structural.cycleReductionPercent, structural.architectureHealthDelta);
        }
        return result;
    }
}
