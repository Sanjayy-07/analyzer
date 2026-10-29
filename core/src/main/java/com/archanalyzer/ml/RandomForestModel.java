package com.archanalyzer.ml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PART 4: "PRIMARY MODEL: RANDOM FOREST CLASSIFIER."
 * ---------------------------------------------------------------------------
 * Loads the exact ensemble of decision trees scikit-learn produced in
 * dataset-pipeline/train_model.py (export_random_forest) and performs real
 * tree-traversal inference in pure Java — every tree's split thresholds and
 * leaf class-probability distributions are the ones scikit-learn actually
 * learned; this class does not re-implement training, only faithful
 * inference over an externally-trained ensemble.
 */
public class RandomForestModel {

    private String[] featureNames;
    private String[] classes;
    private double[] featureImportances;
    private final List<Tree> trees = new ArrayList<>();

    private static class Node {
        boolean leaf;
        int feature;
        double threshold;
        int left = -1, right = -1;
        double[] leafProbs;
    }

    private static class Tree {
        Node[] nodes;
        double[] predict(double[] x) {
            int cur = 0;
            while (!nodes[cur].leaf) {
                Node n = nodes[cur];
                cur = (x[n.feature] <= n.threshold) ? n.left : n.right;
            }
            return nodes[cur].leafProbs;
        }
    }

    private static final Pattern NODE_PATTERN = Pattern.compile(
        "NODE=(\\d+),LEAF=(\\d),(?:FEATURE=(\\d+),THRESHOLD=([\\-0-9.]+),LEFT=(\\d+),RIGHT=(\\d+)|PROBS=([\\-0-9.,]+))");

    public static RandomForestModel loadFromResource(InputStream in) throws IOException {
        RandomForestModel model = new RandomForestModel();
        List<Node> currentNodes = null;
        int currentTreeIndex = -1;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                if (line.startsWith("FEATURES=")) {
                    model.featureNames = line.substring("FEATURES=".length()).split(",");
                } else if (line.startsWith("CLASSES=")) {
                    model.classes = line.substring("CLASSES=".length()).split(",");
                } else if (line.startsWith("FEATURE_IMPORTANCES=")) {
                    model.featureImportances = parseDoubles(line.substring("FEATURE_IMPORTANCES=".length()));
                } else if (line.startsWith("TREE=")) {
                    if (currentNodes != null) model.finishTree(currentNodes);
                    currentNodes = new ArrayList<>();
                } else if (line.startsWith("N_NODES=")) {
                    int n = Integer.parseInt(line.substring("N_NODES=".length()));
                    currentNodes = new ArrayList<>(Collections.nCopies(n, null));
                } else if (line.startsWith("NODE=")) {
                    Matcher m = NODE_PATTERN.matcher(line);
                    if (!m.matches()) throw new IOException("Malformed model node line: " + line);
                    int id = Integer.parseInt(m.group(1));
                    boolean leaf = m.group(2).equals("1");
                    Node node = new Node();
                    node.leaf = leaf;
                    if (leaf) {
                        node.leafProbs = parseDoubles(m.group(7));
                    } else {
                        node.feature = Integer.parseInt(m.group(3));
                        node.threshold = Double.parseDouble(m.group(4));
                        node.left = Integer.parseInt(m.group(5));
                        node.right = Integer.parseInt(m.group(6));
                    }
                    currentNodes.set(id, node);
                }
            }
            if (currentNodes != null) model.finishTree(currentNodes);
        }
        return model;
    }

    private void finishTree(List<Node> nodeList) {
        Tree tree = new Tree();
        tree.nodes = nodeList.toArray(new Node[0]);
        trees.add(tree);
    }

    private static double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    /** Averages per-tree leaf class-probability distributions — the same
     *  aggregation scikit-learn's RandomForestClassifier.predict_proba uses. */
    public Map<String, Double> predictProbabilities(FeatureVector fv) {
        double[] sum = new double[classes.length];
        for (Tree tree : trees) {
            double[] probs = tree.predict(fv.values);
            for (int i = 0; i < probs.length; i++) sum[i] += probs[i];
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < classes.length; i++) result.put(classes[i], sum[i] / trees.size());
        return result;
    }

    public double[] getFeatureImportances() { return featureImportances; }
    public String[] getFeatureNames() { return featureNames; }
    public String[] getClasses() { return classes; }
    public int treeCount() { return trees.size(); }
}
