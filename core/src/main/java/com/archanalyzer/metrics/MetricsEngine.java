package com.archanalyzer.metrics;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;

import java.util.*;

/**
 * PART 10 of the spec: deterministic architecture/software metrics computed
 * from the real dependency graph + component model — no hardcoded or
 * fabricated scores (PART 34).
 */
public class MetricsEngine {

    public static class ComponentMetrics {
        public String componentId;
        public int fanIn, fanOut, loc, cyclomaticComplexity, methodCount, fieldCount, dependencyDepth;
        public double centrality;
        public double instability;     // fanOut / (fanIn + fanOut); 0 = stable, 1 = unstable
        public boolean inCycle;
    }

    public static class ProjectMetrics {
        public int totalComponents;
        public int totalDependencies;
        public int cycleCount;
        public List<Set<String>> cycles;
        public double averageFanOut;
        public double averageCoupling;
        public int maxDependencyDepth;
        public double architectureHealthScore; // 0-100
        public double dependencyScore;         // 0-100 (higher = more dependency pressure -> worse)
        public Map<String, ComponentMetrics> perComponent = new LinkedHashMap<>();
    }

    public ProjectMetrics analyze(ProjectModel model, DependencyGraph graph) {
        ProjectMetrics pm = new ProjectMetrics();
        pm.totalComponents = graph.vertexCount();
        pm.totalDependencies = graph.edgeCount();

        List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
        pm.cycles = sccs.stream().filter(s -> s.size() > 1).toList();
        pm.cycleCount = pm.cycles.size();

        double fanOutSum = 0;
        double couplingSum = 0;
        int maxDepth = 0;

        for (String id : graph.vertices()) {
            ComponentMetrics cm = new ComponentMetrics();
            cm.componentId = id;
            cm.fanIn = graph.fanIn(id);
            cm.fanOut = graph.fanOut(id);
            cm.centrality = GraphAlgorithms.degreeCentrality(graph, id);
            cm.inCycle = GraphAlgorithms.isInCycle(sccs, id);
            cm.dependencyDepth = GraphAlgorithms.longestChainFrom(graph, id, new HashSet<>());
            cm.instability = (cm.fanIn + cm.fanOut) == 0 ? 0.0 : cm.fanOut / (double) (cm.fanIn + cm.fanOut);

            model.getComponent(id).ifPresent(c -> {
                cm.loc = c.getLoc();
                cm.cyclomaticComplexity = c.getCyclomaticComplexity();
                cm.methodCount = c.getMethodCount();
                cm.fieldCount = c.getFieldCount();
            });

            fanOutSum += cm.fanOut;
            couplingSum += (cm.fanIn + cm.fanOut);
            maxDepth = Math.max(maxDepth, cm.dependencyDepth);

            pm.perComponent.put(id, cm);
        }

        int n = Math.max(1, pm.totalComponents);
        pm.averageFanOut = fanOutSum / n;
        pm.averageCoupling = couplingSum / n;
        pm.maxDependencyDepth = maxDepth;

        pm.dependencyScore = computeDependencyScore(pm);
        pm.architectureHealthScore = computeArchitectureHealth(pm);

        return pm;
    }

    /**
     * Dependency Score (0-100): higher means more dependency pressure
     * (worse). Explainable weighted combination of average coupling, cycle
     * presence, and dependency depth, each normalized against reasonable
     * reference scales rather than magic constants pulled from nowhere:
     *  - avg coupling normalized against 20 (an empirically "high" average
     *    fan-in+fan-out per component for a healthy modular design)
     *  - depth normalized against 10 (a chain of 10+ is a strong smell)
     *  - cycles: each cycle contributes fixed pressure, capped
     */
    private double computeDependencyScore(ProjectMetrics pm) {
        double couplingComponent = Math.min(1.0, pm.averageCoupling / 20.0) * 50;
        double depthComponent = Math.min(1.0, pm.maxDependencyDepth / 10.0) * 25;
        double cycleComponent = Math.min(1.0, pm.cycleCount / 5.0) * 25;
        return round1(couplingComponent + depthComponent + cycleComponent);
    }

    /** Architecture Health (0-100): inverse-flavored composite; higher is better. */
    private double computeArchitectureHealth(ProjectMetrics pm) {
        double base = 100 - pm.dependencyScore;
        // Extra penalty for components with cyclomatic complexity that suggests low cohesion / god-classes,
        // averaged in gently so a single outlier doesn't dominate.
        double avgComplexity = pm.perComponent.values().stream()
            .mapToInt(c -> c.cyclomaticComplexity).average().orElse(0);
        double complexityPenalty = Math.min(15, avgComplexity / 2.0);
        return round1(Math.max(0, Math.min(100, base - complexityPenalty)));
    }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
