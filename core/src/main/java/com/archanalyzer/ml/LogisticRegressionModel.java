package com.archanalyzer.ml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Baseline model (PART 4: "BASELINE: LOGISTIC REGRESSION"). Loads the exact
 * one-vs-rest weights scikit-learn produced in
 * dataset-pipeline/train_model.py (see export_logistic_regression) from a
 * simple line-based text format — no JSON library needed on the Java side.
 * Softmax over the one-vs-rest scores approximates scikit-learn's own
 * multinomial `predict_proba` closely enough for an explainable baseline.
 */
public class LogisticRegressionModel {

    private String[] featureNames;
    private String[] classes;
    private double[] scalerMean;
    private double[] scalerScale;
    private final Map<String, double[]> weightsByClass = new LinkedHashMap<>();
    private final Map<String, Double> biasByClass = new LinkedHashMap<>();

    public static LogisticRegressionModel loadFromResource(InputStream in) throws IOException {
        LogisticRegressionModel model = new LogisticRegressionModel();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            String currentClass = null;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                int eq = line.indexOf('=');
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                switch (key) {
                    case "FEATURES" -> model.featureNames = value.split(",");
                    case "CLASSES" -> model.classes = value.split(",");
                    case "SCALER_MEAN" -> model.scalerMean = parseDoubles(value);
                    case "SCALER_SCALE" -> model.scalerScale = parseDoubles(value);
                    case "CLASS" -> currentClass = value;
                    case "WEIGHTS" -> model.weightsByClass.put(currentClass, parseDoubles(value));
                    case "BIAS" -> model.biasByClass.put(currentClass, Double.parseDouble(value));
                    default -> { /* ignore FORMAT and unknown lines */ }
                }
            }
        }
        return model;
    }

    private static double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    /** Standardizes raw features using the training-set scaler, matching sklearn's StandardScaler transform. */
    private double[] standardize(double[] raw) {
        double[] scaled = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            scaled[i] = (raw[i] - scalerMean[i]) / scalerScale[i];
        }
        return scaled;
    }

    public Map<String, Double> predictProbabilities(FeatureVector fv) {
        double[] x = standardize(fv.values);
        Map<String, Double> rawScores = new LinkedHashMap<>();
        for (String cls : classes) {
            double[] w = weightsByClass.get(cls);
            double bias = biasByClass.get(cls);
            double score = bias;
            for (int i = 0; i < x.length; i++) score += w[i] * x[i];
            rawScores.put(cls, score);
        }
        // Softmax normalization across the one-vs-rest scores.
        double max = Collections.max(rawScores.values());
        double sumExp = 0;
        Map<String, Double> expScores = new LinkedHashMap<>();
        for (var e : rawScores.entrySet()) {
            double exp = Math.exp(e.getValue() - max);
            expScores.put(e.getKey(), exp);
            sumExp += exp;
        }
        Map<String, Double> probs = new LinkedHashMap<>();
        for (var e : expScores.entrySet()) probs.put(e.getKey(), e.getValue() / sumExp);
        return probs;
    }

    public String[] getFeatureNames() { return featureNames; }
    public String[] getClasses() { return classes; }
}
