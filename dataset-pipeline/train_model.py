"""
ML TRAINING PIPELINE — Component-level Refactoring-Risk Prediction
=====================================================================
PART 4 / PART 5 of the project spec.

WHAT THIS SCRIPT DOES RIGHT NOW (demo/coursework mode)
-------------------------------------------------------
Generates a SYNTHETIC dataset of component-level software metrics with a
semi-independent, noisy labeling rule (not literally our Java architecture
formula, to avoid the model trivially re-learning our own hand-written
scoring function), trains a Logistic Regression baseline and a Random
Forest classifier, evaluates them with the metrics the spec requires, and
exports both models to a simple, dependency-free text format the Java
inference engine (com.archanalyzer.ml) can load without any JSON/ML
library on the Java side.

THIS IS CLEARLY LABELED DEMO DATA. It exists so the ML layer is a real,
trained, working classifier end-to-end for your demonstration — not a
placeholder. It is NOT a claim that the model reflects real-world
refactoring risk with production-grade accuracy.

HOW TO USE REAL DATA INSTEAD (do this before treating results as valid
research/production output)
-------------------------------------------------------------------------
1. Clone real Java project histories (e.g. from GitHub) and run
   RefactoringMiner (https://github.com/tsantalis/RefactoringMiner) across
   their commit history to extract actual historical refactoring events
   per class/method.
2. For each (project, commit, component) triple, compute the SAME feature
   set as `FEATURE_COLUMNS` below using this project's own Java metrics
   engine (com.archanalyzer.metrics.MetricsEngine) — or an equivalent
   static-analysis pass — run at that commit.
3. Label = 1 (refactoring target) if RefactoringMiner detected a
   refactoring operation touching that component within N commits after
   the snapshot; else 0. (This is what PART 5 means by "Do NOT create
   labels directly from our own architecture-risk formula" — the label
   comes from real historical refactoring events, not our scoring rule.)
4. Replace `build_synthetic_dataset()` below with a loader for your
   assembled CSV. Keep everything downstream (split, train, evaluate,
   export) identical — that pipeline is dataset-agnostic.
5. Use PROJECT-LEVEL (or temporal) splitting, never a random row-level
   split — `train_test_split_by_group()` below already does this; group by
   real repository name/id instead of the synthetic `project_id`.

Run: python3 train_model.py
Requires: numpy, pandas, scikit-learn (already used in this environment).
"""

import json
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    precision_recall_fscore_support, balanced_accuracy_score,
    confusion_matrix, average_precision_score, classification_report
)

RNG = np.random.default_rng(42)

FEATURE_COLUMNS = [
    "loc", "cyclomatic_complexity", "fan_in", "fan_out",
    "dependency_count", "dependency_depth", "coupling",
    "centrality", "cycle_membership", "recent_churn"
]

LABELS = ["LOW", "MEDIUM", "HIGH"]


def build_synthetic_dataset(n_projects=40, components_per_project=60):
    """
    Builds a synthetic but structurally realistic dataset: each synthetic
    "project" has its own baseline noise level (simulating different
    codebase qualities), and each component's features are drawn with
    realistic correlations (e.g., high LOC tends to co-occur with higher
    complexity and fan-out, but with independent noise so the label isn't
    a deterministic function of any single our-own formula).
    """
    rows = []
    for project_id in range(n_projects):
        project_quality = RNG.normal(0, 1)  # worse (higher) = messier codebase overall
        for comp_id in range(components_per_project):
            loc = max(5, int(RNG.gamma(shape=2.0, scale=60) + project_quality * 20))
            complexity = max(1, int(loc / 12 + RNG.normal(0, 3) + project_quality * 2))
            fan_out = max(0, int(RNG.poisson(3 + max(0, project_quality)) + loc / 150))
            fan_in = max(0, int(RNG.poisson(2 + max(0, project_quality * 0.5))))
            dependency_count = fan_in + fan_out + RNG.integers(0, 3)
            dependency_depth = max(0, int(RNG.poisson(2 + max(0, project_quality))))
            coupling = fan_in + fan_out
            centrality = min(1.0, (fan_in + fan_out) / (2 * components_per_project))
            cycle_membership = 1 if RNG.random() < (0.05 + 0.05 * max(0, project_quality)) else 0
            recent_churn = max(0, int(RNG.poisson(2 + max(0, project_quality))))

            # Semi-independent noisy "ground truth" risk score, NOT identical to
            # the Java MetricsEngine's Dependency Score formula — different
            # weighting, different feature subset, injected label noise — so
            # the model must generalize rather than parrot our exact formula.
            latent_risk = (
                0.25 * min(1, complexity / 40)
                + 0.20 * min(1, coupling / 25)
                + 0.20 * cycle_membership
                + 0.15 * min(1, dependency_depth / 8)
                + 0.10 * min(1, recent_churn / 10)
                + 0.10 * min(1, loc / 600)
            )
            latent_risk += RNG.normal(0, 0.08)  # label noise

            if latent_risk < 0.33:
                label = "LOW"
            elif latent_risk < 0.6:
                label = "MEDIUM"
            else:
                label = "HIGH"

            rows.append({
                "project_id": project_id,
                "loc": loc, "cyclomatic_complexity": complexity,
                "fan_in": fan_in, "fan_out": fan_out,
                "dependency_count": dependency_count, "dependency_depth": dependency_depth,
                "coupling": coupling, "centrality": centrality,
                "cycle_membership": cycle_membership, "recent_churn": recent_churn,
                "label": label
            })
    return pd.DataFrame(rows)


def train_test_split_by_group(df, group_col="project_id", test_frac=0.2, seed=42):
    """Project-level split: entire projects go to train OR test, never both
    (PART 5: 'Avoid data leakage... prefer project/repository-level split')."""
    groups = df[group_col].unique()
    rng = np.random.default_rng(seed)
    rng.shuffle(groups)
    n_test = max(1, int(len(groups) * test_frac))
    test_groups = set(groups[:n_test])
    test_df = df[df[group_col].isin(test_groups)]
    train_df = df[~df[group_col].isin(test_groups)]
    return train_df, test_df


def top_k_recall(y_true, y_proba, classes, k_frac=0.2, positive_label="HIGH"):
    """
    Of the top k% of components ranked by predicted P(HIGH), what fraction
    of ALL true-HIGH components are captured? This is the metric the spec
    calls out as 'especially important... to identify which components
    developers should investigate first' (PART 5).
    """
    pos_idx = list(classes).index(positive_label)
    scores = y_proba[:, pos_idx]
    order = np.argsort(-scores)
    k = max(1, int(len(order) * k_frac))
    top_k_idx = order[:k]
    true_positive_total = np.sum(np.array(y_true) == positive_label)
    if true_positive_total == 0:
        return float("nan")
    captured = np.sum(np.array(y_true)[top_k_idx] == positive_label)
    return captured / true_positive_total


def evaluate(name, y_true, y_pred, y_proba, classes):
    precision, recall, f1, _ = precision_recall_fscore_support(
        y_true, y_pred, labels=LABELS, average=None, zero_division=0)
    bal_acc = balanced_accuracy_score(y_true, y_pred)
    cm = confusion_matrix(y_true, y_pred, labels=LABELS)
    # One-vs-rest PR-AUC for the HIGH class (the class we most care about surfacing)
    high_idx = list(classes).index("HIGH")
    y_true_high_binary = (np.array(y_true) == "HIGH").astype(int)
    pr_auc_high = average_precision_score(y_true_high_binary, y_proba[:, high_idx])
    tk_recall = top_k_recall(y_true, y_proba, classes, k_frac=0.2, positive_label="HIGH")

    print(f"\n=== {name} ===")
    for i, lbl in enumerate(LABELS):
        print(f"  {lbl:7s}  precision={precision[i]:.3f}  recall={recall[i]:.3f}  f1={f1[i]:.3f}")
    print(f"  Balanced Accuracy: {bal_acc:.3f}")
    print(f"  PR-AUC (HIGH vs rest): {pr_auc_high:.3f}")
    print(f"  Top-20% Recall (HIGH): {tk_recall:.3f}")
    print(f"  Confusion matrix (rows=true, cols=pred, order={LABELS}):\n{cm}")
    return {
        "balanced_accuracy": bal_acc, "pr_auc_high": pr_auc_high, "top20_recall_high": tk_recall
    }


def export_logistic_regression(model: LogisticRegression, scaler: StandardScaler, path):
    """
    Custom, dependency-free text format the Java LogisticRegressionModel
    class can parse with String.split — avoids requiring a JSON library on
    the Java side. One-vs-rest coefficients (scikit-learn's default for
    multi-class LogisticRegression) are exported per class.
    """
    lines = []
    lines.append("FORMAT=LOGISTIC_REGRESSION_V1")
    lines.append("FEATURES=" + ",".join(FEATURE_COLUMNS))
    lines.append("CLASSES=" + ",".join(model.classes_))
    lines.append("SCALER_MEAN=" + ",".join(f"{v:.6f}" for v in scaler.mean_))
    lines.append("SCALER_SCALE=" + ",".join(f"{v:.6f}" for v in scaler.scale_))
    for i, cls in enumerate(model.classes_):
        coefs = model.coef_[i] if model.coef_.shape[0] > 1 else model.coef_[0]
        intercept = model.intercept_[i] if len(model.intercept_) > 1 else model.intercept_[0]
        lines.append(f"CLASS={cls}")
        lines.append("WEIGHTS=" + ",".join(f"{v:.6f}" for v in coefs))
        lines.append(f"BIAS={intercept:.6f}")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Wrote {path}")


def export_random_forest(model: RandomForestClassifier, path):
    """
    Exports every tree as a flat node list: id,feature_idx,threshold,
    left_id,right_id,leaf_class_probabilities(or -1s if internal node).
    Java's RandomForestModel reconstructs each tree from this list.
    """
    lines = []
    lines.append("FORMAT=RANDOM_FOREST_V1")
    lines.append("FEATURES=" + ",".join(FEATURE_COLUMNS))
    lines.append("CLASSES=" + ",".join(model.classes_))
    lines.append(f"N_TREES={len(model.estimators_)}")
    lines.append("FEATURE_IMPORTANCES=" + ",".join(f"{v:.6f}" for v in model.feature_importances_))

    for t_idx, tree_model in enumerate(model.estimators_):
        tree = tree_model.tree_
        lines.append(f"TREE={t_idx}")
        lines.append(f"N_NODES={tree.node_count}")
        for node_id in range(tree.node_count):
            feature = tree.feature[node_id]
            threshold = tree.threshold[node_id]
            left = tree.children_left[node_id]
            right = tree.children_right[node_id]
            if left == -1 and right == -1:  # leaf
                value = tree.value[node_id][0]
                probs = value / value.sum()
                prob_map = {cls: 0.0 for cls in model.classes_}
                # BUG FIX: inside a RandomForestClassifier, each individual tree's
                # own .classes_ holds LABEL-ENCODED INTEGER positions into the
                # ensemble's model.classes_ (e.g. [0., 1., 2.]), NOT the original
                # string labels — only the ensemble's own classes_ has those.
                # Pairing zip(tree_model.classes_, probs) directly against string
                # keys silently created bogus numeric dict entries and left every
                # real class at its zero-initialized default, so every exported
                # leaf's probabilities were 0.0/0.0/0.0 instead of summing to 1.0.
                for local_encoded_idx, p in zip(tree_model.classes_, probs):
                    global_label = model.classes_[int(local_encoded_idx)]
                    prob_map[global_label] = p
                prob_str = ",".join(f"{prob_map[c]:.6f}" for c in model.classes_)
                lines.append(f"NODE={node_id},LEAF=1,PROBS={prob_str}")
            else:
                lines.append(f"NODE={node_id},LEAF=0,FEATURE={feature},THRESHOLD={threshold:.6f},LEFT={left},RIGHT={right}")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Wrote {path}")


def main():
    df = build_synthetic_dataset()
    train_df, test_df = train_test_split_by_group(df)
    print(f"Train components: {len(train_df)} (from {train_df.project_id.nunique()} projects)")
    print(f"Test components:  {len(test_df)} (from {test_df.project_id.nunique()} projects)")

    X_train = train_df[FEATURE_COLUMNS].values.astype(float)
    y_train = train_df["label"].values
    X_test = test_df[FEATURE_COLUMNS].values.astype(float)
    y_test = test_df["label"].values

    scaler = StandardScaler().fit(X_train)
    X_train_scaled = scaler.transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    # --- Baseline: Logistic Regression ---
    lr = LogisticRegression(max_iter=2000)
    lr.fit(X_train_scaled, y_train)
    lr_pred = lr.predict(X_test_scaled)
    lr_proba = lr.predict_proba(X_test_scaled)
    evaluate("Logistic Regression (baseline)", y_test, lr_pred, lr_proba, lr.classes_)

    # --- Primary model: Random Forest ---
    rf = RandomForestClassifier(
        n_estimators=100, max_depth=8, min_samples_leaf=5,
        class_weight="balanced", random_state=42
    )
    rf.fit(X_train, y_train)  # tree ensembles don't need feature scaling
    rf_pred = rf.predict(X_test)
    rf_proba = rf.predict_proba(X_test)
    evaluate("Random Forest (primary)", y_test, rf_pred, rf_proba, rf.classes_)

    print("\nFeature importances (Random Forest):")
    for feat, imp in sorted(zip(FEATURE_COLUMNS, rf.feature_importances_), key=lambda x: -x[1]):
        print(f"  {feat:22s} {imp:.4f}")

    export_logistic_regression(lr, scaler, "logistic_regression_model.txt")
    export_random_forest(rf, "random_forest_model.txt")

    df.to_csv("synthetic_training_dataset.csv", index=False)
    print("\nWrote synthetic_training_dataset.csv (for inspection/reproducibility)")


if __name__ == "__main__":
    main()
