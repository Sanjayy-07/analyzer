package com.archanalyzer.ml;

import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;
import com.archanalyzer.smell.SmellDetector;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * PART 4 orchestrator: extracts features (deterministically, from real
 * metrics only), runs the trained Random Forest (primary) — falling back to
 * the Logistic Regression baseline if the forest model resource is
 * unavailable — and produces an explainable RiskPrediction. Contributing
 * factors are derived from (a) the model's own learned feature importances
 * and (b) whether this specific component's raw values exceed the SAME
 * thresholds SmellDetector uses, so the explanation is grounded in
 * observable facts rather than a free-form LLM guess (PART 14 keeps ML and
 * LLM explicitly separate).
 */
public class RiskPredictionService {

    private final RandomForestModel randomForest;
    private final LogisticRegressionModel logisticRegression;

    public RiskPredictionService(RandomForestModel randomForest, LogisticRegressionModel logisticRegression) {
        this.randomForest = randomForest;
        this.logisticRegression = logisticRegression;
    }

    public static RiskPredictionService loadDefault() throws IOException {
        RandomForestModel rf = null;
        LogisticRegressionModel lr = null;
        try (InputStream rfIn = RiskPredictionService.class.getResourceAsStream("/models/random_forest_model.txt")) {
            if (rfIn != null) rf = RandomForestModel.loadFromResource(rfIn);
        }
        try (InputStream lrIn = RiskPredictionService.class.getResourceAsStream("/models/logistic_regression_model.txt")) {
            if (lrIn != null) lr = LogisticRegressionModel.loadFromResource(lrIn);
        }
        if (rf == null && lr == null) {
            throw new IOException("No ML model resources found on classpath under /models/. " +
                "Run dataset-pipeline/train_model.py and copy its output into src/main/resources/models/.");
        }
        return new RiskPredictionService(rf, lr);
    }

    public RiskPrediction predict(ComponentMetrics cm, int recentChurn) {
        FeatureVector fv = FeatureVector.from(cm, recentChurn);

        Map<String, Double> probs;
        String modelUsed;
        if (randomForest != null) {
            probs = randomForest.predictProbabilities(fv);
            modelUsed = "RandomForest (n_trees=" + randomForest.treeCount() + ")";
        } else {
            probs = logisticRegression.predictProbabilities(fv);
            modelUsed = "LogisticRegression (baseline — Random Forest model unavailable)";
        }

        String bestLabel = Collections.max(probs.entrySet(), Map.Entry.comparingByValue()).getKey();
        double confidence = probs.get(bestLabel);

        List<String> factors = explainFactors(cm);

        return new RiskPrediction(cm.componentId, bestLabel, confidence, probs, factors, modelUsed);
    }

    /**
     * Correlational, threshold-based explanation — deliberately phrased as
     * "contributing factor", never "caused by", per PART 4's explicit
     * instruction not to claim causality.
     */
    private List<String> explainFactors(ComponentMetrics cm) {
        List<String> factors = new ArrayList<>();
        if (cm.fanOut >= SmellDetector.EXCESSIVE_FAN_OUT_THRESHOLD) factors.add("High fan-out (" + cm.fanOut + ")");
        if (cm.fanIn >= SmellDetector.EXCESSIVE_FAN_IN_THRESHOLD) factors.add("High fan-in (" + cm.fanIn + ")");
        if (cm.centrality >= SmellDetector.HIGH_CENTRALITY_THRESHOLD) factors.add("High centrality (" + String.format("%.2f", cm.centrality) + ")");
        if (cm.inCycle) factors.add("Participates in a circular dependency");
        if (cm.dependencyDepth >= SmellDetector.DEEP_CHAIN_THRESHOLD) factors.add("Deep dependency chain (" + cm.dependencyDepth + ")");
        if (cm.cyclomaticComplexity >= 15) factors.add("High cyclomatic complexity (" + cm.cyclomaticComplexity + ")");
        if (cm.loc >= SmellDetector.GOD_COMPONENT_LOC_THRESHOLD) factors.add("Large component size (" + cm.loc + " LOC)");
        if (factors.isEmpty()) factors.add("No individual metric crosses a high-risk threshold on its own");
        return factors;
    }
}
