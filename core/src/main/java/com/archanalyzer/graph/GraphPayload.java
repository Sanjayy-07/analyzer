package com.archanalyzer.graph;

import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.util.*;

/**
 * Serialisable graph snapshot used by the dashboard and before/after simulator views.
 */
public final class GraphPayload {

    private GraphPayload() {}

    public static Map<String, Object> from(ProjectModel model, DependencyGraph graph, ProjectMetrics metrics,
                                           List<Smell> smells, List<RiskPrediction> risks,
                                           Set<String> highlightAdded, Set<String> highlightRemoved,
                                           Set<String> newNodes) {
        Map<String, Smell> smellByComponent = new LinkedHashMap<>();
        if (smells != null) {
            for (Smell s : smells) {
                smellByComponent.merge(s.componentId, s, (a, b) ->
                    a.severity.ordinal() >= b.severity.ordinal() ? a : b);
            }
        }
        Map<String, RiskPrediction> riskByComponent = new LinkedHashMap<>();
        if (risks != null) {
            for (RiskPrediction r : risks) {
                riskByComponent.put(r.componentId, r);
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        if (graph != null) {
            for (String id : graph.vertices()) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", id);
                Optional<ProjectModel.ComponentNode> cn = model != null ? model.getComponent(id) : Optional.empty();
                String label = cn.map(ProjectModel.ComponentNode::getSimpleName)
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> shortName(id));
                n.put("label", shortName(label));
                String kind = cn.map(c -> c.getKind().name()).orElse(
                    (newNodes != null && newNodes.contains(id)) ? "INTERFACE" : "CLASS");
                n.put("kind", kind);
                n.put("file", cn.map(ProjectModel.ComponentNode::getDeclaredInFile).orElse(""));
                n.put("packageName", cn.map(ProjectModel.ComponentNode::getPackageName).orElse(packageOf(id)));
                n.put("layer", cn.map(c -> String.join(",", c.getLayer())).orElse(""));
                ComponentMetrics cm = metrics != null ? metrics.perComponent.get(id) : null;
                n.put("fanIn", cm != null ? cm.fanIn : graph.fanIn(id));
                n.put("fanOut", cm != null ? cm.fanOut : graph.fanOut(id));
                n.put("instability", cm != null ? cm.instability : 0);
                n.put("centrality", cm != null ? cm.centrality : 0);
                n.put("complexity", cm != null ? cm.cyclomaticComplexity : 0);
                n.put("loc", cm != null ? cm.loc : 0);
                n.put("methodCount", cm != null ? cm.methodCount : 0);
                n.put("inCycle", cm != null && cm.inCycle);
                Smell smell = smellByComponent.get(id);
                n.put("smell", smell != null ? smell.type : "");
                n.put("smellEvidence", smell != null ? smell.evidence : "");
                RiskPrediction risk = riskByComponent.get(id);
                n.put("risk", risk != null ? risk.riskLabel : "");
                n.put("riskConfidence", risk != null ? risk.confidence : 0);
                n.put("riskFactors", risk != null ? risk.topContributingFactors : List.of());
                n.put("proposed", newNodes != null && newNodes.contains(id));
                nodes.add(n);
            }
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        if (graph != null) {
            for (String from : graph.vertices()) {
                for (String to : graph.outgoing(from)) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("from", from);
                    e.put("to", to);
                    e.put("id", from + "->" + to);
                    String key = from + "->" + to;
                    String change = "unchanged";
                    if (highlightAdded != null && highlightAdded.contains(key)) change = "added";
                    if (highlightRemoved != null && highlightRemoved.contains(key)) change = "removed";
                    e.put("change", change);
                    edges.add(e);
                }
            }
        }
        if (highlightRemoved != null && graph != null) {
            for (String key : highlightRemoved) {
                String[] parts = key.split("->", 2);
                if (parts.length == 2 && !graph.outgoing(parts[0]).contains(parts[1])) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("from", parts[0]);
                    e.put("to", parts[1]);
                    e.put("id", key);
                    e.put("change", "removed");
                    edges.add(e);
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        return payload;
    }

    public static String shortName(String id) {
        if (id == null || id.isBlank()) return "";
        String s = id.trim();
        while (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    private static String packageOf(String id) {
        if (id == null || id.isBlank()) return "";
        String s = id.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            return s.substring(0, slash).replace('\\', '/');
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(0, dot) : "";
    }
}
