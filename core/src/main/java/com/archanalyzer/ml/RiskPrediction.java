package com.archanalyzer.ml;

import java.util.List;
import java.util.Map;

/**
 * PART 4: the UI must show risk label, model confidence, and contributing
 * factors WITHOUT claiming any feature "caused" the prediction.
 */
public class RiskPrediction {
    public final String componentId;
    public final String riskLabel;              // LOW / MEDIUM / HIGH
    public final double confidence;             // 0.0-1.0, the predicted class's probability
    public final Map<String, Double> classProbabilities;
    public final List<String> topContributingFactors; // descriptive, correlational — not causal claims
    public final String modelUsed;              // "RandomForest" or "LogisticRegression(baseline)"

    public RiskPrediction(String componentId, String riskLabel, double confidence,
                           Map<String, Double> classProbabilities,
                           List<String> topContributingFactors, String modelUsed) {
        this.componentId = componentId;
        this.riskLabel = riskLabel;
        this.confidence = confidence;
        this.classProbabilities = classProbabilities;
        this.topContributingFactors = topContributingFactors;
        this.modelUsed = modelUsed;
    }
}
