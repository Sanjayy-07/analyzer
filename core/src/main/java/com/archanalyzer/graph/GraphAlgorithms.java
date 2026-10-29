package com.archanalyzer.graph;

import java.util.*;

/**
 * PART 9 of the spec: BFS, DFS, cycle detection, Strongly Connected
 * Components (Tarjan's algorithm), topological sorting, reachability, and
 * centrality — all implemented directly (no third-party graph library),
 * against the DependencyGraph built from the Common Project Model.
 */
public final class GraphAlgorithms {

    private GraphAlgorithms() {}

    /** Breadth-first traversal from a start vertex; returns visit order. */
    public static List<String> bfs(DependencyGraph g, String start) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            order.add(v);
            for (String next : g.outgoing(v)) {
                if (visited.add(next)) queue.add(next);
            }
        }
        return order;
    }

    /** Depth-first traversal from a start vertex; returns visit order. */
    public static List<String> dfs(DependencyGraph g, String start) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        dfsVisit(g, start, visited, order);
        return order;
    }

    private static void dfsVisit(DependencyGraph g, String v, Set<String> visited, List<String> order) {
        if (!visited.add(v)) return;
        order.add(v);
        for (String next : g.outgoing(v)) dfsVisit(g, next, visited, order);
    }

    /** All vertices reachable from start (forward reachability — "what does this affect if changed"). */
    public static Set<String> reachableFrom(DependencyGraph g, String start) {
        return new LinkedHashSet<>(bfs(g, start));
    }

    /** All vertices that can reach `target` (reverse reachability — "what depends on this"). */
    public static Set<String> dependentsOf(DependencyGraph g, String target) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(target);
        visited.add(target);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String prev : g.incoming(v)) {
                if (visited.add(prev)) queue.add(prev);
            }
        }
        visited.remove(target);
        return visited;
    }

    /**
     * Tarjan's Strongly Connected Components algorithm. Any SCC with more
     * than one member (or a single self-loop, which we already exclude at
     * graph-build time) represents a circular dependency.
     */
    public static List<Set<String>> stronglyConnectedComponents(DependencyGraph g) {
        TarjanState state = new TarjanState();
        for (String v : g.vertices()) {
            if (!state.indices.containsKey(v)) tarjanStrongConnect(g, v, state);
        }
        return state.sccs;
    }

    private static void tarjanStrongConnect(DependencyGraph g, String v, TarjanState state) {
        state.indices.put(v, state.index);
        state.lowlink.put(v, state.index);
        state.index++;
        state.stack.push(v);
        state.onStack.add(v);

        for (String w : g.outgoing(v)) {
            if (!state.indices.containsKey(w)) {
                tarjanStrongConnect(g, w, state);
                state.lowlink.put(v, Math.min(state.lowlink.get(v), state.lowlink.get(w)));
            } else if (state.onStack.contains(w)) {
                state.lowlink.put(v, Math.min(state.lowlink.get(v), state.indices.get(w)));
            }
        }

        if (state.lowlink.get(v).equals(state.indices.get(v))) {
            Set<String> scc = new LinkedHashSet<>();
            String w;
            do {
                w = state.stack.pop();
                state.onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            state.sccs.add(scc);
        }
    }

    private static class TarjanState {
        int index = 0;
        final Map<String, Integer> indices = new HashMap<>();
        final Map<String, Integer> lowlink = new HashMap<>();
        final Deque<String> stack = new ArrayDeque<>();
        final Set<String> onStack = new HashSet<>();
        final List<Set<String>> sccs = new ArrayList<>();
    }

    /** True if the SCC containing `v` has more than one member (i.e. v participates in a cycle). */
    public static boolean isInCycle(List<Set<String>> sccs, String v) {
        return sccs.stream().anyMatch(scc -> scc.size() > 1 && scc.contains(v));
    }

    /**
     * Kahn's algorithm topological sort. Returns empty Optional if the graph
     * has a cycle (topological order is undefined for cyclic graphs).
     */
    public static Optional<List<String>> topologicalSort(DependencyGraph g) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String v : g.vertices()) inDegree.put(v, g.fanIn(v));

        Deque<String> queue = new ArrayDeque<>();
        for (String v : g.vertices()) if (inDegree.get(v) == 0) queue.add(v);

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String v = queue.poll();
            order.add(v);
            for (String next : g.outgoing(v)) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) queue.add(next);
            }
        }
        if (order.size() != g.vertexCount()) return Optional.empty(); // cycle present
        return Optional.of(order);
    }

    /**
     * Longest dependency chain length starting at `v` (used for "dependency
     * depth" metric). Cycle-safe via a visiting set to avoid infinite
     * recursion on circular graphs.
     */
    public static int longestChainFrom(DependencyGraph g, String v, Set<String> visiting) {
        if (visiting.contains(v)) return 0; // cycle guard
        visiting.add(v);
        int max = 0;
        for (String next : g.outgoing(v)) {
            max = Math.max(max, 1 + longestChainFrom(g, next, visiting));
        }
        visiting.remove(v);
        return max;
    }

    /**
     * Degree centrality normalized by (n-1): (fan-in + fan-out) / (2 * (n-1)).
     * A simple, explainable centrality measure appropriate for an
     * architecture-risk context, as opposed to more expensive measures like
     * betweenness centrality (O(V*E), unnecessary at this scale/purpose).
     */
    public static double degreeCentrality(DependencyGraph g, String v) {
        int n = g.vertexCount();
        if (n <= 1) return 0.0;
        return (g.fanIn(v) + g.fanOut(v)) / (double) (2 * (n - 1));
    }
}
