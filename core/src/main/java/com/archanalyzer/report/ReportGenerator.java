package com.archanalyzer.report;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.optimization.ArchitectureCandidate;
import com.archanalyzer.smell.SmellDetector.Smell;
import com.archanalyzer.validate.ValidationAggregator.ValidationResult;
import com.archanalyzer.validate.ValidationAggregator.Verdict;

import java.time.Instant;
import java.util.List;

/**
 * Generates Markdown analysis and refactoring report for Java projects.
 *
 * All metrics, smells, dependency facts, risk predictions, and refactoring
 * results are derived exclusively from Java source analysis.
 */
public class ReportGenerator {

    public String generate(ProjectModel model, ProjectMetrics before, ProjectMetrics after,
                            List<Smell> smells, List<RiskPrediction> riskPredictions,
                            List<ArchitectureCandidate> candidates, ArchitectureCandidate selected,
                            List<ArchitectureCandidate> rejected, ValidationResult validation) {
        StringBuilder r = new StringBuilder();
        r.append("# Java Architecture Refactoring Report\n\n");
        r.append("Generated: ").append(Instant.now()).append("\n\n");

        r.append("## 1. Project Information\n");
        r.append("- Project: ").append(model.getProjectName()).append("\n");
        r.append("- Java source files analyzed: ").append(model.fileCount()).append("\n");
        r.append("- Java components analyzed: ").append(model.componentCount()).append("\n");
        r.append("- Build tool detected: ").append(model.getBuildInfo().getOrDefault("buildTool", "unknown")).append("\n\n");

        r.append("## 2. Language & Analysis Scope\n");
        r.append("- Supported language: **Java** (only supported programming language)\n");
        if (model.isMixedLanguage()) {
            r.append("- Mixed-language project detected: **").append(model.getExcludedNonJavaFiles().size())
             .append("** non-Java source file(s) across languages [")
             .append(String.join(", ", model.getExcludedLanguages()))
             .append("] were excluded from code analysis and preserved intact in the project.\n");
        } else {
            r.append("- Project type: Java-only source code.\n");
        }
        if (model.hasParseErrors()) {
            r.append("- **Parse warnings/errors** encountered in ").append(model.getParseErrors().size()).append(" file(s):\n");
            model.getParseErrors().forEach((file, err) -> r.append("  - `").append(file).append("`: ").append(err).append("\n"));
        }
        r.append("\n");

        r.append("## 3-4. Dependency Summary & Architecture Health (Before)\n");
        r.append("- Architecture Health: ").append(before.architectureHealthScore).append(" / 100\n");
        r.append("- Dependency Score: ").append(before.dependencyScore).append(" / 100\n");
        r.append("- Total Internal Dependencies: ").append(before.totalDependencies).append("\n");
        r.append("- Cycles Detected: ").append(before.cycleCount).append("\n\n");

        r.append("## 5. Architectural Smells\n");
        if (smells.isEmpty()) {
            r.append("- No architectural smells detected.\n");
        } else {
            smells.stream().limit(30).forEach(s ->
                r.append("- [").append(s.severity).append("] ").append(s.type).append(" — ")
                 .append(s.componentId).append(" (").append(s.evidence).append(")\n"));
            if (smells.size() > 30) r.append("- ... and ").append(smells.size() - 30).append(" more\n");
        }
        r.append("\n");

        r.append("## 6. ML Risk Predictions\n");
        if (riskPredictions.isEmpty()) {
            r.append("- No components available for ML risk prediction.\n");
        } else {
            riskPredictions.stream()
                .sorted((a, b) -> Double.compare(b.confidence, a.confidence))
                .limit(15)
                .forEach(p -> r.append("- ").append(p.componentId).append(": **").append(p.riskLabel)
                    .append("** (confidence ").append(String.format("%.0f%%", p.confidence * 100))
                    .append(", model=").append(p.modelUsed).append(") — factors: ")
                    .append(String.join("; ", p.topContributingFactors)).append("\n"));
        }
        r.append("\n");

        r.append("## 7-8. Candidate Architectures & Scores\n");
        if (candidates.isEmpty()) {
            r.append("- No candidates generated for this project.\n");
        } else {
            for (ArchitectureCandidate c : candidates) {
                r.append("- ").append(c.id).append(" [").append(c.strategyName).append(" -> ")
                 .append(c.targetComponentId).append("] score=").append(c.compositeScore)
                 .append(" projectedDependencyScore=").append(c.projectedDependencyScore)
                 .append(" status=").append(c.status).append("\n");
            }
        }
        r.append("\n");

        if (!rejected.isEmpty()) {
            r.append("## Rejected Candidates\n");
            for (ArchitectureCandidate c : rejected) {
                r.append("- ").append(c.id).append(" (").append(c.strategyName).append("): ")
                 .append(c.rejectionReason).append("\n");
            }
            r.append("\n");
        }

        r.append("## 9. Selected Architecture & Refactoring Execution\n");
        if (selected != null) {
            r.append("- Strategy: ").append(selected.strategyName).append("\n");
            r.append("- Target: ").append(selected.targetComponentId).append("\n");
            r.append("- Status: **").append(selected.status).append("**\n");
            if (validation != null && validation.verdict == Verdict.FAIL) {
                r.append("- Outcome: **REJECTED & ROLLED BACK** — Candidate failed validation; deliverable ZIP contains the preserved original project.\n");
            }
            r.append("- Rationale: ").append(selected.rationale).append("\n");
            r.append("- Planned/Applied changes:\n");
            selected.plannedChanges.forEach(pc -> r.append("  - ").append(pc).append("\n"));
        } else {
            r.append("- No candidate was selected/applied in this run.\n");
        }
        r.append("\n");

        r.append("## 10. Before / After Metrics\n");
        r.append("| Metric | Before | After |\n|---|---|---|\n");
        r.append("| Architecture Health | ").append(before.architectureHealthScore).append(" | ")
         .append(after != null ? after.architectureHealthScore : before.architectureHealthScore).append(" |\n");
        r.append("| Dependency Score | ").append(before.dependencyScore).append(" | ")
         .append(after != null ? after.dependencyScore : before.dependencyScore).append(" |\n");
        r.append("| Cycles | ").append(before.cycleCount).append(" | ")
         .append(after != null ? after.cycleCount : before.cycleCount).append(" |\n\n");

        r.append("## 11. Validation Result\n");
        if (validation != null) {
            r.append("- Verdict: **").append(validation.verdict).append("**\n");
            r.append("- Summary: ").append(validation.summary).append("\n");
            if (validation.buildResult != null) {
                r.append("- Compiler: ").append(validation.buildResult.buildToolUsed).append(" (status: ")
                 .append(validation.buildResult.status).append(")\n");
                r.append("- Test/Build Tool: ").append(validation.buildResult.testSummary).append("\n");
                if (!validation.buildResult.errors.isEmpty()) {
                    r.append("- Build errors:\n");
                    validation.buildResult.errors.forEach(e -> r.append("  - ").append(e).append("\n"));
                }
            }
        } else {
            r.append("- No refactoring was performed (analysis-only mode).\n");
        }
        r.append("\n");

        r.append("## 12. Scope and Honesty Notes\n");
        r.append("- Java is the only supported programming language. Non-Java source files are excluded from code analysis.\n");
        r.append("- Interface Extraction is genuinely applied and recompiled; Dependency Inversion and Facade Introduction are preview-only recommendations in this build.\n");
        r.append("- Validation distinguishes genuine compiler checks from external build/test tool execution, never fabricating test results.\n");

        return r.toString();
    }
}
