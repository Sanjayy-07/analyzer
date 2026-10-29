package com.archanalyzer.validate;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PART 20: "Before refactoring: Create a baseline." Captures the exact
 * measured state so before/after comparison (PART 22) is a real diff, not
 * an assertion.
 */
public class Baseline {
    public final Instant capturedAt = Instant.now();
    public double architectureHealth;
    public double dependencyScore;
    public int cycleCount;
    public int totalComponents;
    public int totalDependencies;
    public int highRiskComponentCount;
    public final Map<String, Double> perComponentDependencyContribution = new LinkedHashMap<>();
    public boolean buildSucceeded;
    public Integer testsPassed;
    public Integer testsTotal;

    public static Baseline capture(ProjectMetrics metrics) {
        Baseline b = new Baseline();
        b.architectureHealth = metrics.architectureHealthScore;
        b.dependencyScore = metrics.dependencyScore;
        b.cycleCount = metrics.cycleCount;
        b.totalComponents = metrics.totalComponents;
        b.totalDependencies = metrics.totalDependencies;
        return b;
    }
}
