package com.archanalyzer.impact;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;

import java.util.*;

/**
 * PART 12: "What happens if I change this component?" — answered via real
 * graph traversal (reverse reachability = who depends on it), never via an
 * LLM guess.
 */
public class ChangeImpactAnalyzer {

    public static class ImpactReport {
        public String componentId;
        public Set<String> directImpact = new LinkedHashSet<>();     // immediate dependents (fan-in neighbors)
        public Set<String> indirectImpact = new LinkedHashSet<>();   // transitive dependents beyond direct
        public int maxDepth;
        public double impactScore; // 0-100
    }

    public ImpactReport analyze(DependencyGraph graph, String componentId) {
        ImpactReport report = new ImpactReport();
        report.componentId = componentId;

        report.directImpact.addAll(graph.incoming(componentId));

        Set<String> allDependents = GraphAlgorithms.dependentsOf(graph, componentId);
        report.indirectImpact.addAll(allDependents);
        report.indirectImpact.removeAll(report.directImpact);

        report.maxDepth = bfsDepth(graph, componentId, allDependents);

        int totalProjectComponents = Math.max(1, graph.vertexCount() - 1);
        double breadthFactor = allDependents.size() / (double) totalProjectComponents;
        double depthFactor = Math.min(1.0, report.maxDepth / 8.0);
        double centralityFactor = GraphAlgorithms.degreeCentrality(graph, componentId);

        report.impactScore = round1(100 * (0.5 * breadthFactor + 0.3 * depthFactor + 0.2 * centralityFactor));
        return report;
    }

    /** BFS depth (in the reverse/incoming direction) to the farthest dependent. */
    private int bfsDepth(DependencyGraph graph, String start, Set<String> dependents) {
        if (dependents.isEmpty()) return 0;
        Map<String, Integer> depth = new HashMap<>();
        depth.put(start, 0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        int max = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String prev : graph.incoming(v)) {
                if (!depth.containsKey(prev)) {
                    int d = depth.get(v) + 1;
                    depth.put(prev, d);
                    max = Math.max(max, d);
                    queue.add(prev);
                }
            }
        }
        return max;
    }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
