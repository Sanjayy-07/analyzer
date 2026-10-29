package com.archanalyzer.graph;

import com.archanalyzer.model.ProjectModel;
import java.util.*;

/**
 * Directed graph G = (V, E) built strictly from *internal* project
 * dependencies (edges whose target component actually exists in the
 * project — edges pointing at unresolved/third-party types are dropped
 * here, since we cannot legitimately graph what we didn't observe as a
 * project component; PART 6: "The ML model must NOT invent dependency
 * facts" applies equally to the deterministic engine).
 *
 * Advanced Java syllabus mapping: Collections Framework (Module 1) —
 * Map/Set/List used throughout; this class is intentionally built on plain
 * JDK collections rather than a graph library, so the syllabus's Collections
 * topic is genuinely exercised rather than delegated.
 */
public class DependencyGraph {

    private final Set<String> vertices = new LinkedHashSet<>();
    private final Map<String, Set<String>> adjacency = new LinkedHashMap<>();      // outgoing: from -> {to}
    private final Map<String, Set<String>> reverseAdjacency = new LinkedHashMap<>(); // incoming: to -> {from}
    private final List<ProjectModel.DependencyEdge> rawEdges = new ArrayList<>();

    public static DependencyGraph buildFrom(ProjectModel model) {
        DependencyGraph graph = new DependencyGraph();
        for (ProjectModel.ComponentNode c : model.getComponents()) {
            graph.addVertex(c.getId());
        }
        for (ProjectModel.DependencyEdge edge : model.getDependencies()) {
            if (graph.vertices.contains(edge.getFrom()) && graph.vertices.contains(edge.getTo())
                    && !edge.getFrom().equals(edge.getTo())) {
                graph.addEdge(edge.getFrom(), edge.getTo());
                graph.rawEdges.add(edge);
            }
        }
        return graph;
    }

    public void addVertex(String id) {
        vertices.add(id);
        adjacency.computeIfAbsent(id, k -> new LinkedHashSet<>());
        reverseAdjacency.computeIfAbsent(id, k -> new LinkedHashSet<>());
    }

    public void addEdge(String from, String to) {
        addVertex(from);
        addVertex(to);
        adjacency.get(from).add(to);
        reverseAdjacency.get(to).add(from);
    }

    public void removeEdge(String from, String to) {
        adjacency.computeIfAbsent(from, k -> new LinkedHashSet<>()).remove(to);
        reverseAdjacency.computeIfAbsent(to, k -> new LinkedHashSet<>()).remove(from);
        rawEdges.removeIf(e -> e.getFrom().equals(from) && e.getTo().equals(to));
    }

    /** Isolated copy used by what-if simulation so the baseline graph is never mutated. */
    public DependencyGraph copy() {
        DependencyGraph clone = new DependencyGraph();
        for (String v : vertices) {
            clone.addVertex(v);
        }
        for (String from : vertices) {
            for (String to : outgoing(from)) {
                clone.addEdge(from, to);
            }
        }
        clone.rawEdges.addAll(this.rawEdges);
        return clone;
    }

    public Set<String> vertices() { return Collections.unmodifiableSet(vertices); }
    public Set<String> outgoing(String v) { return adjacency.getOrDefault(v, Set.of()); }
    public Set<String> incoming(String v) { return reverseAdjacency.getOrDefault(v, Set.of()); }
    public int fanOut(String v) { return outgoing(v).size(); }
    public int fanIn(String v) { return incoming(v).size(); }
    public int edgeCount() { return adjacency.values().stream().mapToInt(Set::size).sum(); }
    public int vertexCount() { return vertices.size(); }
    public boolean containsVertex(String v) { return vertices.contains(v); }
    public List<ProjectModel.DependencyEdge> rawEdges() { return rawEdges; }
}
