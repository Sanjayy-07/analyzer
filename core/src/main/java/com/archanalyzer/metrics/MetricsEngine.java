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
        int concreteEdges = 0;
        int abstractEdges = 0;
        int interfaceCount = 0;

        for (String id : graph.vertices()) {
            ComponentMetrics cm = new ComponentMetrics();
            cm.componentId = id;
            cm.fanIn = graph.fanIn(id);
            cm.fanOut = graph.fanOut(id);
            cm.centrality = GraphAlgorithms.degreeCentrality(graph, id);
            cm.inCycle = GraphAlgorithms.isInCycle(sccs, id);
            cm.dependencyDepth = GraphAlgorithms.longestChainFrom(graph, id, new HashSet<>());
            cm.instability = (cm.fanIn + cm.fanOut) == 0 ? 0.0 : cm.fanOut / (double) (cm.fanIn + cm.fanOut);

            if (model != null) {
                model.getComponent(id).ifPresent(c -> {
                    cm.loc = c.getLoc();
                    cm.cyclomaticComplexity = c.getCyclomaticComplexity();
                    cm.methodCount = c.getMethodCount();
                    cm.fieldCount = c.getFieldCount();
                    if (c.getKind() == com.archanalyzer.model.ProjectModel.ComponentNode.Kind.INTERFACE) {
                        // Handled below per vertex
                    }
                });
                var optC = model.getComponent(id);
                if (optC.isPresent() && optC.get().getKind() == com.archanalyzer.model.ProjectModel.ComponentNode.Kind.INTERFACE) {
                    interfaceCount++;
                }
            }

            for (String targetId : graph.outgoing(id)) {
                boolean isInterface = false;
                if (model != null) {
                    var targetNode = model.getComponent(targetId);
                    if (targetNode.isPresent() && targetNode.get().getKind() == com.archanalyzer.model.ProjectModel.ComponentNode.Kind.INTERFACE) {
                        isInterface = true;
                    }
                }
                if (isInterface) abstractEdges++;
                else concreteEdges++;
            }

            fanOutSum += cm.fanOut;
            couplingSum += (cm.fanIn + cm.fanOut);
            maxDepth = Math.max(maxDepth, cm.dependencyDepth);

            pm.perComponent.put(id, cm);
        }

        int n = Math.max(1, pm.totalComponents);
        pm.averageFanOut = round1(fanOutSum / n);
        pm.averageCoupling = round1(couplingSum / n);
        pm.maxDependencyDepth = maxDepth;

        pm.dependencyScore = computeDependencyScore(pm, concreteEdges, abstractEdges, n);
        pm.architectureHealthScore = computeArchitectureHealth(pm, interfaceCount, n);

        return pm;
    }

    /**
     * Dependency Score (0-100): higher means more dependency pressure (worse).
     * Distinguishes rigid concrete coupling (weight 1.0) from loose interface abstraction coupling (weight 0.35).
     */
    private double computeDependencyScore(ProjectMetrics pm, int concreteEdges, int abstractEdges, int n) {
        double weightedEdges = concreteEdges * 1.0 + abstractEdges * 0.35;
        double effectiveCoupling = (weightedEdges * 2.0) / (double) n;

        double couplingComponent = Math.min(1.0, effectiveCoupling / 20.0) * 50;
        double depthComponent = Math.min(1.0, pm.maxDependencyDepth / 10.0) * 25;
        double cycleComponent = Math.min(1.0, pm.cycleCount / 5.0) * 25;

        // Relief rewarded for concrete coupling decoupled into interface contracts
        double abstractionRelief = Math.min(10.0, abstractEdges * 0.45);
        return round1(Math.max(0, (couplingComponent + depthComponent + cycleComponent) - abstractionRelief));
    }

    /**
     * Architecture Health (0-100): composite health rewarded for interface contracts / DIP compliance.
     */
    private double computeArchitectureHealth(ProjectMetrics pm, int interfaceCount, int n) {
        double base = 100 - pm.dependencyScore;
        double avgComplexity = pm.perComponent.values().stream()
            .mapToInt(c -> c.cyclomaticComplexity).average().orElse(0);
        double complexityPenalty = Math.min(15, avgComplexity / 2.0);

        // Abstraction bonus rewarded for Dependency Inversion Principle (DIP contracts)
        double abstractionBonus = Math.min(12.0, interfaceCount * 2.5);

        return round1(Math.max(0, Math.min(100, base - complexityPenalty + abstractionBonus)));
    }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
