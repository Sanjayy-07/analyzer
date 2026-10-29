package com.archanalyzer.optimization;

import java.util.*;

/**
 * PART 18: when the user rejects a candidate, the system must never simply
 * re-show it. This class tracks rejected (strategy, target) pairs and hands
 * back the next-best remaining ranked candidate, generating a fresh batch
 * via CandidateGenerator only if the current ranked list is exhausted.
 */
public class RejectRegenerateManager {

    private final Set<String> rejectedStrategyTargetPairs = new LinkedHashSet<>();
    private final Map<String, String> rejectionReasons = new LinkedHashMap<>();
    private final Deque<ArchitectureCandidate> rankedPool = new ArrayDeque<>();

    public void loadPool(List<ArchitectureCandidate> rankedCandidates) {
        rankedPool.clear();
        rankedPool.addAll(rankedCandidates);
    }

    public void reject(ArchitectureCandidate candidate, String reason) {
        candidate.status = "REJECTED";
        candidate.rejectionReason = reason;
        String key = candidate.strategyName + "::" + candidate.targetComponentId;
        rejectedStrategyTargetPairs.add(key);
        if (reason != null) rejectionReasons.put(key, reason);
        rankedPool.remove(candidate);
    }

    /** @return the next-best candidate not yet rejected, or empty if the pool is exhausted. */
    public Optional<ArchitectureCandidate> nextBest() {
        while (!rankedPool.isEmpty()) {
            ArchitectureCandidate next = rankedPool.peekFirst();
            String key = next.strategyName + "::" + next.targetComponentId;
            if (rejectedStrategyTargetPairs.contains(key)) {
                rankedPool.pollFirst();
                continue;
            }
            return Optional.of(next);
        }
        return Optional.empty();
    }

    public Set<String> getRejectedStrategyTargetPairs() { return rejectedStrategyTargetPairs; }
    public boolean isPoolExhausted() { return rankedPool.isEmpty(); }
}
