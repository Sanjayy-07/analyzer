package com.archanalyzer.pipeline;

import com.archanalyzer.ai.AiAnalysisResult;
import com.archanalyzer.ai.AiExplanationService;
import com.archanalyzer.archive.*;
import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.impact.ChangeImpactAnalyzer;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.ml.RiskPredictionService;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.optimization.*;
import com.archanalyzer.refactor.RefactoringChange;
import com.archanalyzer.refactor.RefactoringEngine;
import com.archanalyzer.report.ReportGenerator;
import com.archanalyzer.simulator.*;
import com.archanalyzer.smell.SmellDetector;
import com.archanalyzer.smell.SmellDetector.Smell;
import com.archanalyzer.validate.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Orchestrator for the Code Analysis, What-If Simulation, and Refactoring pipeline:
 *
 * UPLOAD -> SAFE EXTRACTION -> PROJECT DISCOVERY -> MULTI-LANGUAGE PARSING ->
 * COMMON PROJECT MODEL -> DEPENDENCY GRAPH -> ARCHITECTURE METRICS ->
 * CODE SMELL DETECTION -> ML RISK PREDICTION -> WHAT-IF SIMULATION ->
 * RISK ASSESSMENT -> USER APPROVAL -> ISOLATED IMPLEMENTATION ->
 * RE-ANALYSIS -> VALIDATION -> REPORT + DELIVERABLE ZIP.
 */
public class AnalyzerPipeline {

    /** Strategies RefactoringEngine can actually materialize. */
    public static final Set<String> AUTO_APPLIABLE_STRATEGIES = Set.of("Interface Extraction");

    private final ArchiveExtractorFactory extractorFactory = new ArchiveExtractorFactory();
    private final ProjectDiscovery discovery;
    private final MetricsEngine metricsEngine = new MetricsEngine();
    private final SmellDetector smellDetector = new SmellDetector();
    private final ChangeImpactAnalyzer impactAnalyzer = new ChangeImpactAnalyzer();
    private final CandidateGenerator candidateGenerator = new CandidateGenerator();
    private final ScoringEngine scoringEngine = new ScoringEngine();
    private final RejectRegenerateManager rejectManager = new RejectRegenerateManager();
    private final WhatIfSimulator whatIfSimulator = new WhatIfSimulator();
    private final ChangeProposalParser proposalParser = new ChangeProposalParser();
    private final ChangeImplementationEngine implementationEngine = new ChangeImplementationEngine();
    private final RefactoringEngine refactoringEngine;
    private final BuildValidator buildValidator;
    private final StructuralValidator structuralValidator;
    private final ValidationAggregator validationAggregator;
    private final ReportGenerator reportGenerator = new ReportGenerator();
    private final ProjectPackager packager = new ProjectPackager();
    private final AiExplanationService aiService;
    private RiskPredictionService riskService;

    public AnalyzerPipeline() {
        this(new ProjectDiscovery(), new BuildValidator(), new StructuralValidator(), new ValidationAggregator(), new RefactoringEngine(), new AiExplanationService());
    }

    public AnalyzerPipeline(boolean javaOnlyMode) {
        this(new ProjectDiscovery(javaOnlyMode), new BuildValidator(), new StructuralValidator(), new ValidationAggregator(), new RefactoringEngine(), new AiExplanationService());
    }

    public AnalyzerPipeline(ProjectDiscovery discovery) {
        this(discovery, new BuildValidator(), new StructuralValidator(), new ValidationAggregator(), new RefactoringEngine(), new AiExplanationService());
    }

    public AnalyzerPipeline(ValidationAggregator validationAggregator) {
        this(new ProjectDiscovery(), new BuildValidator(), new StructuralValidator(), validationAggregator, new RefactoringEngine(), new AiExplanationService());
    }

    public AnalyzerPipeline(RefactoringEngine refactoringEngine) {
        this(new ProjectDiscovery(), new BuildValidator(), new StructuralValidator(), new ValidationAggregator(), refactoringEngine, new AiExplanationService());
    }

    public AnalyzerPipeline(BuildValidator buildValidator, StructuralValidator structuralValidator,
                            ValidationAggregator validationAggregator) {
        this(new ProjectDiscovery(), buildValidator, structuralValidator, validationAggregator, new RefactoringEngine(), new AiExplanationService());
    }

    public AnalyzerPipeline(BuildValidator buildValidator, StructuralValidator structuralValidator,
                            ValidationAggregator validationAggregator, RefactoringEngine refactoringEngine) {
        this(new ProjectDiscovery(), buildValidator, structuralValidator, validationAggregator, refactoringEngine, new AiExplanationService());
    }

    public AnalyzerPipeline(ProjectDiscovery discovery, BuildValidator buildValidator, StructuralValidator structuralValidator,
                            ValidationAggregator validationAggregator, RefactoringEngine refactoringEngine) {
        this(discovery, buildValidator, structuralValidator, validationAggregator, refactoringEngine, new AiExplanationService());
    }

    public AnalyzerPipeline(ProjectDiscovery discovery, BuildValidator buildValidator, StructuralValidator structuralValidator,
                            ValidationAggregator validationAggregator, RefactoringEngine refactoringEngine,
                            AiExplanationService aiService) {
        this.discovery = discovery != null ? discovery : new ProjectDiscovery();
        this.buildValidator = buildValidator != null ? buildValidator : new BuildValidator();
        this.structuralValidator = structuralValidator != null ? structuralValidator : new StructuralValidator();
        this.validationAggregator = validationAggregator != null ? validationAggregator : new ValidationAggregator();
        this.refactoringEngine = refactoringEngine != null ? refactoringEngine : new RefactoringEngine();
        this.aiService = aiService != null ? aiService : new AiExplanationService();
    }

    public static class PipelineResult {
        public ProjectModel model;
        public DependencyGraph graph;
        public ProjectMetrics metricsBefore, metricsAfter;
        public List<Smell> smells;
        public List<RiskPrediction> riskPredictions;
        public AiAnalysisResult aiResult;
        public List<ArchitectureCandidate> rankedCandidates;
        public ArchitectureCandidate selectedCandidate;
        public List<ArchitectureCandidate> rejectedCandidates = new ArrayList<>();
        public ValidationAggregator.ValidationResult validation;
        public SimulationResult simulationResult;
        public String reportMarkdown;
        public Path finalZipPath;
        public Path projectRoot;
        public Path refactoredProjectRoot;
    }

    public SimulationResult simulate(PipelineResult baseline,
                                     String naturalLanguagePrompt,
                                     String explicitTarget,
                                     ChangeOperationType explicitOperation) {
        return simulate(baseline, naturalLanguagePrompt, explicitTarget, explicitOperation, null);
    }

    public SimulationResult simulate(PipelineResult baseline,
                                     String naturalLanguagePrompt,
                                     String explicitTarget,
                                     ChangeOperationType explicitOperation,
                                     String alternativeId) {
        if (baseline == null || baseline.model == null || baseline.graph == null) {
            throw new IllegalArgumentException("Cannot run simulation without a valid baseline project analysis.");
        }
        if (riskService == null) {
            try {
                riskService = RiskPredictionService.loadDefault();
            } catch (Exception ignored) {
                // ML remains unavailable; heuristic risk scoring still runs.
            }
        }
        ChangeRequest req = proposalParser.parse(naturalLanguagePrompt, explicitTarget, explicitOperation, baseline.model);
        SimulationResult simResult = whatIfSimulator.simulate(
            req, baseline.model, baseline.graph, baseline.metricsBefore, riskService, baseline.smells, alternativeId);
        baseline.simulationResult = simResult;
        return simResult;
    }

    public ChangeImplementationEngine.ImplementationResult implementChange(PipelineResult baseline,
                                                                           SimulationResult simResult,
                                                                           Path workDir) throws Exception {
        if (baseline == null || baseline.projectRoot == null) {
            throw new IllegalArgumentException("Invalid project baseline root.");
        }
        simResult.setApproved(true);
        var implResult = implementationEngine.executeApprovedChange(simResult, baseline.projectRoot, workDir);
        if (implResult.success) {
            baseline.finalZipPath = implResult.finalZipPath;
            baseline.metricsAfter = implResult.metricsAfter;
        } else if (implResult.rolledBack) {
            baseline.finalZipPath = implResult.finalZipPath;
        }
        return implResult;
    }

    /**
     * Runs the full Java analysis and refactoring pipeline.
     */
    public PipelineResult run(Path archiveOrDirectory, Path workDir, String projectName,
                                ConstraintSet constraints) throws Exception {
        PipelineResult result = new PipelineResult();

        // 1. SAFE EXTRACTION (or pass-through copy if already a directory)
        Path projectRoot = workDir.resolve("extracted");
        result.projectRoot = projectRoot;
        if (Files.isDirectory(archiveOrDirectory)) {
            copyDirectory(archiveOrDirectory, projectRoot);
        } else {
            Optional<ArchiveExtractor> extractor = extractorFactory.resolve(archiveOrDirectory);
            if (extractor.isEmpty()) {
                String likely = extractorFactory.identifyLikelyFormat(archiveOrDirectory);
                if (extractorFactory.isKnownUnsupported(likely)) {
                    throw new UnsupportedOperationException(
                        "Archive format " + likely + " is not supported by this build. Please re-upload as ZIP or TAR.GZ.");
                }
                throw new UnsupportedOperationException("Could not identify a supported archive format for: " + archiveOrDirectory);
            }
            extractor.get().extract(archiveOrDirectory, projectRoot);
        }

        // 2-6. JAVA DISCOVERY, PARSING, COMMON PROJECT MODEL
        ProjectModel model = discovery.discoverAndParse(projectRoot, projectName);
        result.model = model;

        // 7. JAVA DEPENDENCY GRAPH
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        result.graph = graph;

        // 8. ARCHITECTURE METRICS
        ProjectMetrics before = metricsEngine.analyze(model, graph);
        result.metricsBefore = before;

        // 9. ARCHITECTURAL SMELL DETECTION
        List<Smell> smells = smellDetector.detect(before, graph);
        result.smells = smells;

        // 10. ML RISK PREDICTION
        if (riskService == null) {
            try {
                riskService = RiskPredictionService.loadDefault();
            } catch (Exception e) {
                System.err.println("WARNING: ML risk prediction model could not be loaded: " + e.getMessage());
            }
        }
        List<RiskPrediction> risks = new ArrayList<>();
        if (riskService != null) {
            for (var cm : before.perComponent.values()) {
                risks.add(riskService.predict(cm, 0)); // recentChurn=0: plain archive upload
            }
        }
        result.riskPredictions = risks;

        // 10b. GENERATIVE AI ARCHITECTURE REASONING & RECOMMENDATIONS
        result.aiResult = aiService.analyzeArchitecture(model, before, smells, risks);

        // 11-13. CANDIDATE GENERATION & RANKING
        List<ArchitectureCandidate> candidates = candidateGenerator.generate(before, smells, Set.of());
        List<ArchitectureCandidate> ranked = scoringEngine.rankValid(candidates, constraints,
            before.dependencyScore, before.cycleCount);
        rejectManager.loadPool(ranked);
        result.rankedCandidates = ranked;

        // Label preview candidates honestly
        for (ArchitectureCandidate c : ranked) {
            if (!AUTO_APPLIABLE_STRATEGIES.contains(c.strategyName)) {
                c.status = "Preview Only — Not Applied";
            }
        }

        // 14. SELECT CANDIDATE FOR AUTOMATED REFACTORING
        ArchitectureCandidate selected = null;
        for (ArchitectureCandidate c : ranked) {
            if (AUTO_APPLIABLE_STRATEGIES.contains(c.strategyName)) {
                selected = c;
                break;
            }
        }

        if (selected == null) {
            // No candidate in this run is auto-appliable.
            Path finalZip = workDir.resolve(projectName + "-analyzed.zip");
            packager.packageToZip(projectRoot, finalZip);
            result.finalZipPath = finalZip;
            result.reportMarkdown = reportGenerator.generate(model, before, before, smells, risks,
                ranked, null, result.rejectedCandidates, null);
            return result;
        }

        selected.status = "ACCEPTED";
        result.selectedCandidate = selected;

        // 15. AUTOMATED REFACTORING ON ISOLATED WORKING COPY
        Path refactoredRoot = workDir.resolve("refactored");
        result.refactoredProjectRoot = refactoredRoot;
        copyDirectory(projectRoot, refactoredRoot);

        Optional<ProjectModel.ComponentNode> target = model.getComponent(selected.targetComponentId);
        if (target.isPresent()) {
            try {
                Path targetFile = refactoredRoot.resolve(target.get().getDeclaredInFile());
                List<RefactoringChange> changes = refactoringEngine.extractInterface(
                    refactoredRoot, targetFile, target.get().getSimpleName());
                for (RefactoringChange c : changes) {
                    selected.plannedChanges.add("APPLIED: " + c);
                }
                selected.status = "APPLIED";
            } catch (Exception e) {
                selected.status = "FAILED";
                selected.plannedChanges.add("FAILED: " + e.getMessage());
                safeRollback(projectRoot, refactoredRoot);
                Path finalZip = workDir.resolve(projectName + "-original.zip");
                packager.packageToZip(projectRoot, finalZip);
                result.finalZipPath = finalZip;
                result.reportMarkdown = reportGenerator.generate(model, before, before, smells, risks,
                    ranked, selected, result.rejectedCandidates, null);
                return result;
            }
        } else {
            selected.status = "SKIPPED";
            selected.plannedChanges.add("SKIPPED: Target component not found in model");
            safeRollback(projectRoot, refactoredRoot);
            Path finalZip = workDir.resolve(projectName + "-original.zip");
            packager.packageToZip(projectRoot, finalZip);
            result.finalZipPath = finalZip;
            result.reportMarkdown = reportGenerator.generate(model, before, before, smells, risks,
                ranked, selected, result.rejectedCandidates, null);
            return result;
        }

        // 16. RE-ANALYSIS AGAINST MODIFIED PROJECT
        ProjectModel modelAfter = discovery.discoverAndParse(refactoredRoot, projectName);
        DependencyGraph graphAfter = DependencyGraph.buildFrom(modelAfter);
        ProjectMetrics after = metricsEngine.analyze(modelAfter, graphAfter);
        result.metricsAfter = after;

        // 17. BUILD / TEST / STRUCTURAL VALIDATION
        Baseline baseline = Baseline.capture(before);
        BuildValidator.BuildResult buildResult = buildValidator.compile(refactoredRoot, List.of(), projectRoot);
        buildValidator.runExternalBuildToolValidation(refactoredRoot,
            model.getBuildInfo().getOrDefault("buildTool", "unknown"), buildResult);

        StructuralValidator.ComparisonResult comparison = structuralValidator.compare(baseline, after, modelAfter);
        ValidationAggregator.ValidationResult validation = validationAggregator.aggregate(buildResult, comparison);
        result.validation = validation;

        // 18. PASS OR RECOVERY
        if (validation.verdict == ValidationAggregator.Verdict.FAIL) {
            rejectManager.reject(selected, validation.summary);
            selected.status = "VALIDATED_FAIL";
            result.rejectedCandidates.add(selected);
            Optional<ArchitectureCandidate> recovery = rejectManager.nextBest();
            recovery.ifPresent(c -> c.status = "PROPOSED (recovery candidate — not auto-applied in this run)");

            // Roll back the working copy so rejected changes do not leak into disk or deliverable
            safeRollback(projectRoot, refactoredRoot);

            // 19. FINAL ZIP: Deliver original project because candidate was rejected
            Path finalZip = workDir.resolve(projectName + "-original.zip");
            packager.packageToZip(projectRoot, finalZip);
            result.finalZipPath = finalZip;
        } else {
            selected.status = "VALIDATED_" + validation.verdict;

            // 19. FINAL ZIP: Deliver accepted refactored project
            Path finalZip = workDir.resolve(projectName + "-refactored.zip");
            packager.packageToZip(refactoredRoot, finalZip);
            result.finalZipPath = finalZip;
        }

        result.reportMarkdown = reportGenerator.generate(modelAfter, before, after, smells, risks,
            ranked, selected, result.rejectedCandidates, validation);

        return result;
    }

    private void safeRollback(Path pristineSource, Path targetDir) {
        if (targetDir == null) return;
        deleteRecursive(targetDir);
        if (pristineSource != null && Files.exists(pristineSource)) {
            copyDirectory(pristineSource, targetDir);
        }
    }

    private void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path)) return;
        System.gc();
        try (var walk = Files.walk(path)) {
            var list = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : list) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void copyDirectory(Path source, Path target) {
        if (source == null || !Files.exists(source)) return;
        try (var walk = Files.walk(source)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
