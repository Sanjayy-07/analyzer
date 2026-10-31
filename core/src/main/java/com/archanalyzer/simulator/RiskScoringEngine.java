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
        ComponentNode targetNode = targetId != null && model != null ? model.getComponent(targetId).orElse(null) : null;
        ComponentMetrics targetMetrics = targetId != null && metrics != null ? metrics.perComponent.get(targetId) : null;

        int totalAffected = directImpact.size() + indirectImpact.size();
        Set<String> allAffected = new LinkedHashSet<>(directImpact);
        allAffected.addAll(indirectImpact);

        // 1. Downstream Blast Radius Magnitude (0-30 pts)
        // Calibrated to real-world software engineering blast radius thresholds:
        // A change cascading across 10-15 components is a widespread ripple effect.
        double breadthScore;
        if (totalAffected == 0) {
            breadthScore = 0.0;
            assessment.contributingFactors.add("Component is isolated: 0 downstream dependents affected.");
        } else if (totalAffected == 1) {
            breadthScore = 8.0;
            assessment.contributingFactors.add("Localized impact: 1 downstream component directly affected.");
        } else if (totalAffected <= 4) {
            breadthScore = 15.0;
            assessment.contributingFactors.add(String.format("Subsystem cascade: %d downstream components affected (%d direct, %d indirect).",
                totalAffected, directImpact.size(), indirectImpact.size()));
        } else if (totalAffected <= 9) {
            breadthScore = 22.0;
            assessment.contributingFactors.add(String.format("Broad domain cascade: %d downstream components affected (%d direct, %d indirect).",
                totalAffected, directImpact.size(), indirectImpact.size()));
        } else if (totalAffected <= 19) {
            breadthScore = 27.0;
            assessment.contributingFactors.add(String.format("Major cross-system cascade: %d downstream components affected (%d direct, %d indirect).",
                totalAffected, directImpact.size(), indirectImpact.size()));
        } else {
            breadthScore = 30.0;
            assessment.contributingFactors.add(String.format("Critical widespread cascade: %d downstream components affected across system.", totalAffected));
        }
        assessment.factorWeights.put("downstreamBreadth", round1(breadthScore));

        // 2. Architectural Tier & API Surface Exposure (0-25 pts)
        // Inspects whether the blast radius reaches HTTP REST Controllers, Business Services, or Repositories
        int affectedControllers = 0;
        int affectedServices = 0;
        int affectedRepositories = 0;
        for (String affId : allAffected) {
            ComponentNode cn = model != null ? model.getComponent(affId).orElse(null) : null;
            String lower = affId.toLowerCase();
            boolean isCtrl = (cn != null && cn.getLayer().contains("controller")) || lower.contains("controller") || lower.contains("endpoint") || lower.contains("resource");
            boolean isSrv = (cn != null && cn.getLayer().contains("service")) || lower.contains("service");
            boolean isRepo = (cn != null && (cn.getLayer().contains("repository") || cn.getLayer().contains("dao"))) || lower.contains("repository") || lower.contains("dao");
            if (isCtrl) affectedControllers++;
            else if (isSrv) affectedServices++;
            else if (isRepo) affectedRepositories++;
        }

        double tierScore = 0.0;
        if (affectedControllers > 0) {
            double ctrlPts = Math.min(14.0, affectedControllers * 2.0);
            tierScore += ctrlPts;
            assessment.contributingFactors.add(String.format("High API boundary exposure: %d REST controller(s) in blast radius; external HTTP contracts at risk.", affectedControllers));
        }
        if (affectedServices > 0) {
            double srvPts = Math.min(11.0, affectedServices * 2.0);
            tierScore += srvPts;
            assessment.contributingFactors.add(String.format("Core business logic exposure: %d transactional service(s) in blast radius.", affectedServices));
        }
        if (affectedRepositories > 0) {
            double repoPts = Math.min(8.0, affectedRepositories * 2.0);
            tierScore += repoPts;
            assessment.contributingFactors.add(String.format("Data persistence exposure: %d repository/DAO component(s) in blast radius.", affectedRepositories));
        }
        tierScore = Math.min(25.0, tierScore);
        assessment.factorWeights.put("architecturalTierExposure", round1(tierScore));

        // 3. Impact Propagation Depth (0-15 pts): dependency chain depth to farthest consumer
        double depthScore = Math.min(15.0, maxImpactDepth * 3.5);
        assessment.factorWeights.put("impactDepth", round1(depthScore));
        if (maxImpactDepth > 0) {
            assessment.contributingFactors.add(
                String.format("Impact propagates across %d architectural tier(s) to farthest dependent.", maxImpactDepth)
            );
        }

        // 4. Centrality & Shared Component Coupling (0-15 pts)
        double centrality = targetMetrics != null ? targetMetrics.centrality : 0.0;
        int fanIn = targetMetrics != null ? targetMetrics.fanIn : 0;
        int fanOut = targetMetrics != null ? targetMetrics.fanOut : 0;
        double couplingScore = Math.min(15.0, Math.max(
            (fanIn * 1.5 + fanOut * 0.5) + (centrality * 10.0),
            Math.min(15.0, totalAffected * 1.0)
        ));
        assessment.factorWeights.put("couplingAndCentrality", round1(couplingScore));
        if (totalAffected >= 5 || fanIn >= 3 || centrality > 0.3) {
            assessment.contributingFactors.add(
                String.format("Target is a shared foundational dependency (%d total downstream components depend on it).", totalAffected)
            );
        }

        // 5. Circular Dependency / Smells Factor (0-15 pts)
        double cycleScore = 0.0;
        boolean inCycle = targetMetrics != null && targetMetrics.inCycle;
        if (inCycle) {
            cycleScore = 15.0;
            assessment.contributingFactors.add(
                "Target component participates in a circular dependency cycle, amplifying ripple effects."
            );
        }
        assessment.factorWeights.put("cycleInvolvement", round1(cycleScore));

        // 6. Operation Type & Architectural Layer Criticality (0-15 pts)
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
            case INTRODUCE_INTERFACE -> {
                opCriticalityScore += 6.0;
                assessment.contributingFactors.add("Interface extraction refactoring alters dependency injection and binding contracts.");
            }
            case ADD_FEATURE -> {
                opCriticalityScore += 5.0;
                assessment.contributingFactors.add("Feature addition preserves existing APIs but requires integration testing across callers.");
            }
            default -> opCriticalityScore += 5.0;
        }

        if (targetNode != null) {
            if (targetNode.getLayer().contains("repository") || targetNode.getLayer().contains("model")) {
                opCriticalityScore += 4.0;
                assessment.contributingFactors.add("Target belongs to foundational data/model layer (high architectural criticality).");
            } else if (targetNode.getLayer().contains("service")) {
                opCriticalityScore += 3.0;
            }
        }
        opCriticalityScore = Math.min(15.0, opCriticalityScore);
        assessment.factorWeights.put("operationAndLayer", round1(opCriticalityScore));

        // 7. Test Coverage Assessment (0-10 pts penalty if no test covers it)
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
        double rawHeuristic = breadthScore + tierScore + depthScore + couplingScore + cycleScore + opCriticalityScore + testScore;
        assessment.heuristicScore = Math.min(100.0, Math.max(5.0, rawHeuristic));

        // 8. ML Model Risk Integration (if service available)
        if (riskService != null && targetMetrics != null) {
            try {
                RiskPrediction pred = riskService.predict(targetMetrics, 0);
                double mlScore = "HIGH".equalsIgnoreCase(pred.riskLabel) ? 80.0 :
                                 "MEDIUM".equalsIgnoreCase(pred.riskLabel) ? 50.0 : 25.0;
                assessment.mlPredictedScore = mlScore;
                // Weighted blend: 80% static architecture evidence, 20% ML code defect prediction
                assessment.overallScore = round1((assessment.heuristicScore * 0.8) + (mlScore * 0.2));
            } catch (Exception e) {
                assessment.overallScore = round1(assessment.heuristicScore);
            }
        } else {
            assessment.overallScore = round1(assessment.heuristicScore);
        }

        // Ambiguity penalty
        if (request.isAmbiguous()) {
            assessment.overallScore = Math.min(100.0, assessment.overallScore + 8.0);
            assessment.contributingFactors.add("Uncertainty penalty: target component resolution is ambiguous.");
        }

        assessment.overallScore = Math.min(100.0, Math.max(5.0, assessment.overallScore));

        // 9. Classify Risk Category
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
