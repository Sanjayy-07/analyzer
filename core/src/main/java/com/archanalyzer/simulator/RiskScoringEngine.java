package com.archanalyzer.simulator;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.ml.RiskPredictionService;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;

import java.util.*;

/**
 * Transparent, deterministic, and ML-integrated Risk Scoring Engine.
 * Computes an objective risk score from 0 to 100 with quantified contributing factors.
 */
public class RiskScoringEngine {

    public static class RiskAssessment {
        public double overallScore;          // 0 - 100
        public String riskCategory;          // "Very Low", "Low", "Moderate", "High", "Critical"
        public double heuristicScore;        // deterministic structural score
        public Double mlPredictedScore;      // ML model score if available
        public double confidence;            // 0.0 - 1.0
        public List<String> contributingFactors = new ArrayList<>();
        public Map<String, Double> factorWeights = new LinkedHashMap<>();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("overallScore", round1(overallScore));
            map.put("riskCategory", riskCategory);
            map.put("heuristicScore", round1(heuristicScore));
            map.put("mlPredictedScore", mlPredictedScore != null ? round1(mlPredictedScore) : null);
            map.put("confidence", round1(confidence * 100));
            map.put("contributingFactors", contributingFactors);
            map.put("factorWeights", factorWeights);
            return map;
        }

        private static double round1(double v) {
            return Math.round(v * 10.0) / 10.0;
        }
    }

    public RiskAssessment assess(ChangeRequest request,
                                 ProjectModel model,
                                 DependencyGraph graph,
                                 ProjectMetrics metrics,
                                 Set<String> directImpact,
                                 Set<String> indirectImpact,
                                 int maxImpactDepth,
                                 RiskPredictionService riskService) {
        RiskAssessment assessment = new RiskAssessment();

        String targetId = request.getTargetComponentId();
        ComponentNode targetNode = targetId != null ? model.getComponent(targetId).orElse(null) : null;
        ComponentMetrics targetMetrics = targetId != null && metrics != null ? metrics.perComponent.get(targetId) : null;

        int totalComponents = Math.max(1, graph != null ? graph.vertexCount() : model.componentCount());
        int totalAffected = directImpact.size() + indirectImpact.size();

        // 1. Breadth Factor (0-25 pts): fraction of project components affected
        double breadthRatio = Math.min(1.0, (double) totalAffected / (double) totalComponents);
        double breadthScore = breadthRatio * 25.0;
        assessment.factorWeights.put("downstreamBreadth", round1(breadthScore));
        if (totalAffected > 0) {
            assessment.contributingFactors.add(
                String.format("%d downstream component(s) affected (%d direct, %d indirect) out of %d total (%.0f%% project scope)",
                    totalAffected, directImpact.size(), indirectImpact.size(), totalComponents, breadthRatio * 100)
            );
        } else {
            assessment.contributingFactors.add("Component is isolated; no downstream project components directly depend on it.");
        }

        // 2. Depth Factor (0-15 pts): dependency chain depth to farthest dependent
        double depthScore = Math.min(1.0, maxImpactDepth / 6.0) * 15.0;
        assessment.factorWeights.put("impactDepth", round1(depthScore));
        if (maxImpactDepth > 0) {
            assessment.contributingFactors.add(
                String.format("Impact propagates across %d architectural dependency tier(s).", maxImpactDepth)
            );
        }

        // 3. Centrality & Coupling Factor (0-20 pts)
        double centrality = targetMetrics != null ? targetMetrics.centrality : 0.0;
        int fanIn = targetMetrics != null ? targetMetrics.fanIn : 0;
        int fanOut = targetMetrics != null ? targetMetrics.fanOut : 0;
        double couplingScore = Math.min(1.0, (fanIn * 0.7 + fanOut * 0.3) / 10.0) * 12.0 + (centrality * 8.0);
        assessment.factorWeights.put("couplingAndCentrality", round1(couplingScore));
        if (fanIn >= 3 || centrality > 0.3) {
            assessment.contributingFactors.add(
                String.format("Target component has high coupling (fan-in=%d, fan-out=%d, centrality=%.2f).",
                    fanIn, fanOut, centrality)
            );
        }

        // 4. Circular Dependency / Smells Factor (0-15 pts)
        double cycleScore = 0.0;
        boolean inCycle = targetMetrics != null && targetMetrics.inCycle;
        if (inCycle) {
            cycleScore = 15.0;
            assessment.contributingFactors.add(
                "Target component participates in a circular dependency cycle, amplifying ripple effects."
            );
        }
        assessment.factorWeights.put("cycleInvolvement", round1(cycleScore));

        // 5. Operation Type & Architectural Layer Criticality (0-15 pts)
        double opCriticalityScore = 0.0;
        ChangeOperationType op = request.getOperation() != null ? request.getOperation() : ChangeOperationType.OTHER;
        switch (op) {
            case REMOVE_FEATURE, REMOVE_API -> {
                opCriticalityScore += 12.0;
                assessment.contributingFactors.add("Removal operations carry elevated risk of broken references and orphaned dependents.");
            }
            case MODIFY_DATABASE_ENTITY -> {
                opCriticalityScore += 14.0;
                assessment.contributingFactors.add("Database entity modifications impact persistent storage, queries, and ORM contracts.");
            }
            case MODIFY_API -> {
                opCriticalityScore += 10.0;
                assessment.contributingFactors.add("Modifying API contracts introduces backward-compatibility and serialization risks.");
            }
            case ADD_MODULE -> {
                opCriticalityScore += 8.0;
                assessment.contributingFactors.add("Introducing a new module requires establishing correct layering to prevent circular imports.");
            }
            case ADD_FEATURE -> {
                opCriticalityScore += 5.0;
                assessment.contributingFactors.add("Feature addition preserves existing APIs but requires integration testing across callers.");
            }
            default -> opCriticalityScore += 5.0;
        }

        // Layer adjustment
        if (targetNode != null) {
            if (targetNode.getLayer().contains("repository") || targetNode.getLayer().contains("model")) {
                opCriticalityScore += 5.0;
                assessment.contributingFactors.add("Target belongs to foundational data/model layer (high architectural criticality).");
            } else if (targetNode.getLayer().contains("service")) {
                opCriticalityScore += 3.0;
            }
        }
        assessment.factorWeights.put("operationAndLayer", round1(opCriticalityScore));

        // 6. Test Coverage Assessment (0-10 pts penalty if no test covers it)
        double testScore = 0.0;
        boolean hasTest = hasCoveringTest(targetId, model);
        if (!hasTest) {
            testScore = 10.0;
            assessment.contributingFactors.add(
                "No dedicated unit test detected for the target component; changes lack automated test safety."
            );
        } else {
            assessment.contributingFactors.add("Automated unit test detected covering component functionality.");
        }
        assessment.factorWeights.put("testCoverageDeficit", round1(testScore));

        // Compute deterministic heuristic score
        double rawHeuristic = breadthScore + depthScore + couplingScore + cycleScore + opCriticalityScore + testScore;
        assessment.heuristicScore = Math.min(100.0, Math.max(5.0, rawHeuristic));

        // 7. ML Model Risk Integration (if service available)
        if (riskService != null && targetMetrics != null) {
            try {
                RiskPrediction pred = riskService.predict(targetMetrics, 0);
                double mlScore = 20.0;
                if ("HIGH".equalsIgnoreCase(pred.riskLabel)) {
                    mlScore = 80.0 * pred.confidence;
                    assessment.contributingFactors.add(
                        String.format("Trained Random Forest ML model predicts HIGH structural risk (%.0f%% confidence).",
                            pred.confidence * 100)
                    );
                } else if ("MEDIUM".equalsIgnoreCase(pred.riskLabel)) {
                    mlScore = 50.0 * pred.confidence;
                    assessment.contributingFactors.add(
                        String.format("Trained Random Forest ML model predicts MEDIUM structural risk (%.0f%% confidence).",
                            pred.confidence * 100)
                    );
                } else {
                    mlScore = 15.0;
                    assessment.contributingFactors.add(
                        String.format("Trained Random Forest ML model predicts LOW baseline risk (%.0f%% confidence).",
                            pred.confidence * 100)
                    );
                }
                assessment.mlPredictedScore = mlScore;
                // Weighted blend: 70% static architecture evidence, 30% ML classifier
                assessment.overallScore = round1((assessment.heuristicScore * 0.7) + (mlScore * 0.3));
            } catch (Exception e) {
                assessment.overallScore = round1(assessment.heuristicScore);
            }
        } else {
            assessment.overallScore = round1(assessment.heuristicScore);
        }

        // Ambiguity penalty
        if (request.isAmbiguous()) {
            assessment.overallScore = Math.min(100.0, assessment.overallScore + 10.0);
            assessment.contributingFactors.add("Uncertainty penalty: target component resolution is ambiguous.");
        }

        assessment.overallScore = Math.min(100.0, Math.max(0.0, assessment.overallScore));

        // 8. Classify Risk Category
        if (assessment.overallScore >= 80.0) {
            assessment.riskCategory = "Critical";
        } else if (assessment.overallScore >= 60.0) {
            assessment.riskCategory = "High";
        } else if (assessment.overallScore >= 40.0) {
            assessment.riskCategory = "Moderate";
        } else if (assessment.overallScore >= 20.0) {
            assessment.riskCategory = "Low";
        } else {
            assessment.riskCategory = "Very Low";
        }

        assessment.confidence = request.getConfidence();
        return assessment;
    }

    private boolean hasCoveringTest(String componentId, ProjectModel model) {
        if (componentId == null || model == null) return false;
        String simple = componentId.substring(componentId.lastIndexOf('.') + 1);
        String testName1 = simple + "Test";
        String testName2 = "Test" + simple;

        for (ComponentNode cn : model.getComponents()) {
            String cSimple = cn.getSimpleName();
            if (cSimple.equalsIgnoreCase(testName1) || cSimple.equalsIgnoreCase(testName2)) {
                return true;
            }
        }
        for (var file : model.getFiles()) {
            String path = file.getRelativePath().toLowerCase();
            if (path.contains("test") && (path.contains(simple.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
