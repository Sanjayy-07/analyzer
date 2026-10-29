package com.archanalyzer.ml;

import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;

/**
 * MUST stay in exactly this order — it mirrors FEATURE_COLUMNS in
 * dataset-pipeline/train_model.py so a model exported from Python lines up
 * correctly with vectors built here. If you add/remove/reorder a feature on
 * either side, retrain and re-export before using the model again.
 */
public class FeatureVector {

    public static final String[] FEATURE_NAMES = {
        "loc", "cyclomatic_complexity", "fan_in", "fan_out",
        "dependency_count", "dependency_depth", "coupling",
        "centrality", "cycle_membership", "recent_churn"
    };

    public final double[] values;

    private FeatureVector(double[] values) {
        this.values = values;
    }

    /**
     * Builds a feature vector purely from deterministically computed
     * metrics (PART 6: "the ML model must NOT invent dependency facts").
     * `recentChurn` defaults to 0 when version-control history isn't
     * available to the analyzer (e.g. a plain archive upload with no .git
     * folder) — this is an honest "unknown = 0" rather than a fabricated
     * value, and is documented as a known limitation.
     */
    public static FeatureVector from(ComponentMetrics cm, int recentChurn) {
        double[] v = new double[FEATURE_NAMES.length];
        v[0] = cm.loc;
        v[1] = cm.cyclomaticComplexity;
        v[2] = cm.fanIn;
        v[3] = cm.fanOut;
        v[4] = cm.fanIn + cm.fanOut;
        v[5] = cm.dependencyDepth;
        v[6] = cm.fanIn + cm.fanOut;
        v[7] = cm.centrality;
        v[8] = cm.inCycle ? 1.0 : 0.0;
        v[9] = recentChurn;
        return new FeatureVector(v);
    }
}
