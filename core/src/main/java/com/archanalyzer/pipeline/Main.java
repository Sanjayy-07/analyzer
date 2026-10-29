package com.archanalyzer.pipeline;

import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.optimization.ArchitectureCandidate;
import com.archanalyzer.optimization.ConstraintSet;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Console demonstration entry point (PART 44: "Faculty Demonstration Mode"
 * without needing the full web UI). Run with:
 *   java -cp target/classes com.archanalyzer.pipeline.Main <path-to-project-or-archive> [projectName]
 *
 * Uses the bundled sample demo project (../sample-demo-project) if no
 * argument is given.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("../sample-demo-project");
        String projectName = args.length > 1 ? args[1] : input.getFileName().toString();

        System.out.println("=== Java Architecture Quality Assessment & Automated Refactoring Analyzer ===");
        System.out.println("Input: " + input.toAbsolutePath());

        Path workDir = Files.createTempDirectory("archanalyzer-run-");
        AnalyzerPipeline pipeline = new AnalyzerPipeline();
        AnalyzerPipeline.PipelineResult result;
        try {
            result = pipeline.run(input, workDir, projectName, ConstraintSet.defaults());
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            System.err.println("\n[VALIDATION ERROR] " + e.getMessage());
            return;
        }

        System.out.println("\n--- PROJECT MODEL (JAVA) ---");
        System.out.println("Java Source Files: " + result.model.fileCount() + "   Java Components: " + result.model.componentCount());
        System.out.println("Supported Language: Java");
        if (result.model.isMixedLanguage()) {
            System.out.println("Mixed-Language Notice: " + result.model.getExcludedNonJavaFiles().size() +
                " non-Java file(s) in " + result.model.getExcludedLanguages() + " excluded from code analysis.");
        }
        if (result.model.hasParseErrors()) {
            System.out.println("Parse Warnings/Errors: " + result.model.getParseErrors());
        }

        System.out.println("\n--- METRICS (BEFORE) ---");
        System.out.println("Architecture Health: " + result.metricsBefore.architectureHealthScore + " / 100");
        System.out.println("Dependency Score:    " + result.metricsBefore.dependencyScore + " / 100");
        System.out.println("Cycles:              " + result.metricsBefore.cycleCount);

        System.out.println("\n--- ARCHITECTURAL SMELLS (" + result.smells.size() + ") ---");
        for (Smell s : result.smells) {
            System.out.println("  [" + s.severity + "] " + s.type + " -> " + s.componentId + " (" + s.evidence + ")");
        }

        System.out.println("\n--- ML RISK PREDICTIONS ---");
        List<RiskPrediction> sortedRisks = result.riskPredictions.stream()
            .sorted(Comparator.comparingDouble((RiskPrediction p) -> p.confidence).reversed())
            .toList();
        for (RiskPrediction p : sortedRisks) {
            System.out.printf("  %-45s %-6s (%.0f%% conf, %s) - %s%n",
                p.componentId, p.riskLabel, p.confidence * 100, p.modelUsed,
                String.join("; ", p.topContributingFactors));
            // Full probability breakdown for transparency (PART 4: show the
            // model's reasoning, not just the winning label). These three
            // values should always sum to ~1.0 — if they ever don't, that's
            // a sign the bundled model file needs regenerating via
            // dataset-pipeline/train_model.py.
            p.classProbabilities.forEach((label, prob) ->
                System.out.printf("      probability[%s] = %.4f%n", label, prob));
        }

        System.out.println("\n--- CANDIDATE ARCHITECTURES (ranked) ---");
        for (ArchitectureCandidate c : result.rankedCandidates) {
            System.out.println("  " + c);
        }

        if (result.selectedCandidate != null) {
            System.out.println("\n--- SELECTED CANDIDATE ---");
            System.out.println("  " + result.selectedCandidate);
            result.selectedCandidate.plannedChanges.forEach(pc -> System.out.println("    - " + pc));
        }

        if (result.validation != null) {
            System.out.println("\n--- VALIDATION ---");
            System.out.println("  Verdict: " + result.validation.verdict);
            System.out.println("  " + result.validation.summary);
        }

        if (result.metricsAfter != null) {
            System.out.println("\n--- METRICS (AFTER) ---");
            System.out.println("Architecture Health: " + result.metricsAfter.architectureHealthScore + " / 100");
            System.out.println("Dependency Score:    " + result.metricsAfter.dependencyScore + " / 100");
            System.out.println("Cycles:              " + result.metricsAfter.cycleCount);
        }

        Path reportPath = workDir.resolve("report.md");
        Files.writeString(reportPath, result.reportMarkdown);
        System.out.println("\nReport written to: " + reportPath);
        if (result.finalZipPath != null) {
            if (result.validation != null && result.validation.verdict == com.archanalyzer.validate.ValidationAggregator.Verdict.FAIL) {
                System.out.println("[ROLLBACK] Candidate failed validation. Refactoring was rolled back; original project preserved.");
            }
            System.out.println("Final ZIP written to: " + result.finalZipPath);
        }

        // What-If Simulation CLI Mode
        if (args.length > 2) {
            String simPrompt = args[2];
            System.out.println("\n=======================================================");
            System.out.println("=== WHAT-IF ARCHITECTURE CHANGE SIMULATOR ===");
            System.out.println("=======================================================");
            System.out.println("Change Request Prompt: \"" + simPrompt + "\"");

            var simResult = pipeline.simulate(result, simPrompt, null, null);
            var req = simResult.getRequest();
            var risk = simResult.getRiskAssessment();

            System.out.println("\n--- STRUCTURED CHANGE PROPOSAL ---");
            System.out.println("Request ID:        " + req.getRequestId());
            System.out.println("Operation:         " + req.getOperation());
            System.out.println("Target Component:  " + req.getTargetComponentId());
            System.out.println("Structured Plan:   " + req.getStructuredSummary());
            System.out.println("Confidence:        " + String.format("%.0f%%", risk.confidence * 100));
            if (req.isAmbiguous()) {
                System.out.println("Ambiguity Warning: Multiple candidates matched (" + req.getCandidateMatches() + ")");
            }

            System.out.println("\n--- SIMULATED RISK ASSESSMENT ---");
            System.out.println("Overall Risk Score: " + risk.overallScore + " / 100 [" + risk.riskCategory + " Risk]");
            System.out.println("Contributing Factors:");
            for (String factor : risk.contributingFactors) {
                System.out.println("  * " + factor);
            }

            System.out.println("\n--- CHANGE IMPACT & DEPENDENCY PROPAGATION ---");
            System.out.println("Directly Affected (" + simResult.getDirectlyAffectedComponents().size() + "):   " + simResult.getDirectlyAffectedComponents());
            System.out.println("Indirectly Affected (" + simResult.getIndirectlyAffectedComponents().size() + "): " + simResult.getIndirectlyAffectedComponents());
            if (!simResult.getDependencyPaths().isEmpty()) {
                System.out.println("Dependency Paths to Target:");
                for (var entry : simResult.getDependencyPaths().entrySet()) {
                    System.out.println("  " + String.join(" -> ", entry.getValue()));
                }
            }

            if (!simResult.getPotentiallyBrokenDependencies().isEmpty()) {
                System.out.println("Compatibility Warnings:");
                for (String w : simResult.getPotentiallyBrokenDependencies()) {
                    System.out.println("  ! " + w);
                }
            }

            if (simResult.getProjectedMetrics() != null) {
                System.out.println("\n--- PROJECTED ARCHITECTURE METRICS ---");
                System.out.println("Architecture Health: " + result.metricsBefore.architectureHealthScore + " -> " + simResult.getProjectedMetrics().architectureHealthScore);
                System.out.println("Dependency Pressure: " + result.metricsBefore.dependencyScore + " -> " + simResult.getProjectedMetrics().dependencyScore);
                System.out.println("Cycle Count:         " + result.metricsBefore.cycleCount + " -> " + simResult.getProjectedMetrics().cycleCount);
            }

            Path simReportPath = workDir.resolve("what_if_simulation_report.md");
            Files.writeString(simReportPath, simResult.getSimulationReportMarkdown());
            System.out.println("\nSimulation Report written to: " + simReportPath);

            // Check if user approved implementation from CLI
            boolean approve = args.length > 3 && args[3].equalsIgnoreCase("--approve");
            if (approve) {
                System.out.println("\n--- EXECUTING APPROVED CHANGE ON ISOLATED WORKSPACE ---");
                var implResult = pipeline.implementChange(result, simResult, workDir);
                System.out.println("Status: " + implResult.status);
                System.out.println("Summary: " + implResult.summary);
                for (String c : implResult.appliedChanges) {
                    System.out.println("  + " + c);
                }
                if (implResult.finalZipPath != null) {
                    System.out.println("Deliverable ZIP: " + implResult.finalZipPath);
                }
            } else {
                System.out.println("\n[NOTE] Simulation completed without modifying source files.");
                System.out.println("To execute and validate this change, pass '--approve' as the 4th argument.");
            }
        }
    }
}
