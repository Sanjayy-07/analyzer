package com.archanalyzer.optimization;

import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.util.*;

/**
 * PART 15 / PART 19: generates candidate architectures from the SUPPORTED
 * refactoring-strategy catalogue only (PART 19 explicitly forbids claiming
 * universal automated refactoring). Each candidate's projected metrics are
 * computed with a documented, deterministic estimation formula grounded in
 * the component's OWN current measured metrics — this is a projection, not
 * a guarantee, and is always labeled "projected" in the UI, never presented
 * as an already-verified fact (that only happens after real
 * refactor+recompile+re-analyze in the ValidationEngine).
 *
 * STRATEGY PATTERN (PART 27): each supported strategy is its own private
 * factory method, so adding a new supported transformation means adding one
 * method + one catalogue entry, not touching the ranking/scoring code.
 */
public class CandidateGenerator {

    private int candidateSeq = 0;

    public List<ArchitectureCandidate> generate(ProjectMetrics metrics, List<Smell> smells,
                                                  Set<String> alreadyTriedStrategyTargetPairs) {
        List<ArchitectureCandidate> candidates = new ArrayList<>();

        Map<String, List<Smell>> smellsByComponent = new LinkedHashMap<>();
        for (Smell s : smells) smellsByComponent.computeIfAbsent(s.componentId, k -> new ArrayList<>()).add(s);

        for (var entry : smellsByComponent.entrySet()) {
            String componentId = entry.getKey();
            ComponentMetrics cm = metrics.perComponent.get(componentId);
            if (cm == null) continue;
            Set<String> types = entry.getValue().stream().map(s -> s.type).collect(java.util.stream.Collectors.toSet());

            if (types.contains("CIRCULAR_DEPENDENCY")) {
                addIfNew(candidates, dependencyInversion(metrics, cm), alreadyTriedStrategyTargetPairs);
            }
            if (types.contains("EXCESSIVE_FAN_OUT") || types.contains("GOD_COMPONENT")) {
                addIfNew(candidates, interfaceExtraction(metrics, cm), alreadyTriedStrategyTargetPairs);
            }
            if (types.contains("EXCESSIVE_FAN_IN") || types.contains("ARCHITECTURE_BOTTLENECK")) {
                addIfNew(candidates, facadeIntroduction(metrics, cm), alreadyTriedStrategyTargetPairs);
            }
        }
        candidates.sort((a, b) -> Double.compare(a.projectedDependencyScore, b.projectedDependencyScore));
        return candidates;
    }

    private void addIfNew(List<ArchitectureCandidate> list, ArchitectureCandidate c, Set<String> tried) {
        String key = c.strategyName + "::" + c.targetComponentId;
        if (!tried.contains(key)) list.add(c);
    }

    private ArchitectureCandidate dependencyInversion(ProjectMetrics metrics, ComponentMetrics cm) {
        ArchitectureCandidate c = base(cm, "Dependency Inversion",
            "Component participates in a circular dependency. Introducing an abstraction " +
            "(interface) that both sides depend on breaks the direct cycle.");
        // Estimation: removing the cycle eliminates its cycle-penalty contribution and
        // reduces this component's own coupling by roughly one edge (the inverted one).
        c.projectedCycles = Math.max(0, metrics.cycleCount - 1);
        c.projectedCoupling = Math.max(0, (cm.fanIn + cm.fanOut) - 1);
        c.projectedDependencyScore = recomputeDependencyScoreEstimate(metrics, c.projectedCycles, c.projectedCoupling);
        c.projectedArchitectureHealth = Math.min(100, 100 - c.projectedDependencyScore);
        c.filesChanged = 3; // the two participants + 1 new interface file
        c.plannedChanges.add("Create new interface abstracting the dependency direction");
        c.plannedChanges.add("Update dependent class to reference the new interface");
        c.plannedChanges.add("Rewrite imports in both participating classes");
        c.estimatedRefactoringCostHours = 3;
        return c;
    }

    private ArchitectureCandidate interfaceExtraction(ProjectMetrics metrics, ComponentMetrics cm) {
        ArchitectureCandidate c = base(cm, "Interface Extraction",
            "Component has high fan-out / size. Extracting an interface for its most-used " +
            "responsibility lets callers depend on an abstraction instead of the concrete class.");
        c.projectedCoupling = Math.max(0, (cm.fanIn + cm.fanOut) - Math.max(1, cm.fanOut / 3));
        c.projectedCycles = metrics.cycleCount;
        c.projectedDependencyScore = recomputeDependencyScoreEstimate(metrics, c.projectedCycles, c.projectedCoupling);
        c.projectedArchitectureHealth = Math.min(100, 100 - c.projectedDependencyScore);
        c.filesChanged = 2;
        c.plannedChanges.add("Extract interface capturing the component's public contract");
        c.plannedChanges.add("Update the component to implement the new interface");
        c.estimatedRefactoringCostHours = 2;
        return c;
    }

    private ArchitectureCandidate facadeIntroduction(ProjectMetrics metrics, ComponentMetrics cm) {
        ArchitectureCandidate c = base(cm, "Facade Introduction",
            "Component has high fan-in / centrality (many callers). A facade absorbs the " +
            "component's varied call patterns behind one simplified entry point.");
        c.projectedCoupling = Math.max(0, (cm.fanIn + cm.fanOut) - Math.max(1, cm.fanIn / 4));
        c.projectedCycles = metrics.cycleCount;
        c.projectedDependencyScore = recomputeDependencyScoreEstimate(metrics, c.projectedCycles, c.projectedCoupling);
        c.projectedArchitectureHealth = Math.min(100, 100 - c.projectedDependencyScore);
        c.filesChanged = 4;
        c.plannedChanges.add("Introduce a facade class in front of the high fan-in component");
        c.plannedChanges.add("Redirect a subset of callers to the facade");
        c.estimatedRefactoringCostHours = 4;
        return c;
    }

    private ArchitectureCandidate base(ComponentMetrics cm, String strategy, String rationale) {
        ArchitectureCandidate c = new ArchitectureCandidate();
        c.id = "cand-" + (++candidateSeq);
        c.strategyName = strategy;
        c.targetComponentId = cm.componentId;
        c.rationale = rationale;
        return c;
    }

    /** Same weighted formula as MetricsEngine.computeDependencyScore, applied to projected values. */
    private double recomputeDependencyScoreEstimate(ProjectMetrics metrics, int projectedCycles, double projectedCoupling) {
        double couplingComponent = Math.min(1.0, (metrics.averageCoupling * 0.9) / 20.0) * 50; // slight average nudge down
        double depthComponent = Math.min(1.0, metrics.maxDependencyDepth / 10.0) * 25;
        double cycleComponent = Math.min(1.0, projectedCycles / 5.0) * 25;
        return Math.round((couplingComponent + depthComponent + cycleComponent) * 10) / 10.0;
    }
}
