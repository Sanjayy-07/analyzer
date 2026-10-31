package com.archanalyzer.web.history;

import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manages the in-memory history of uploaded and analyzed Java projects.
 * Allows users to review past project analyses and switch between them dynamically.
 */
public class ProjectHistoryManager {

    public static class HistoryEntry {
        private final String id;
        private final String projectName;
        private final String archiveFileName;
        private final LocalDateTime analyzedAt;
        private final int fileCount;
        private final int componentCount;
        private final double architectureHealth;
        private final double dependencyScore;
        private final int cycleCount;
        private final int smellCount;
        private final PipelineResult result;
        private final Path workDir;

        public HistoryEntry(String id, String projectName, String archiveFileName,
                            PipelineResult result, Path workDir) {
            this.id = id;
            this.projectName = projectName;
            this.archiveFileName = archiveFileName;
            this.analyzedAt = LocalDateTime.now();
            this.result = result;
            this.workDir = workDir;
            this.fileCount = (result != null && result.model != null) ? result.model.fileCount() : 0;
            this.componentCount = (result != null && result.model != null) ? result.model.componentCount() : 0;
            this.architectureHealth = (result != null && result.metricsBefore != null)
                ? result.metricsBefore.architectureHealthScore : 0.0;
            this.dependencyScore = (result != null && result.metricsBefore != null)
                ? result.metricsBefore.dependencyScore : 0.0;
            this.cycleCount = (result != null && result.metricsBefore != null)
                ? result.metricsBefore.cycleCount : 0;
            this.smellCount = (result != null && result.smells != null) ? result.smells.size() : 0;
        }

        public String getId() { return id; }
        public String getProjectName() { return projectName; }
        public String getArchiveFileName() { return archiveFileName; }
        public LocalDateTime getAnalyzedAt() { return analyzedAt; }
        public String getFormattedDate() {
            return analyzedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm:ss"));
        }
        public int getFileCount() { return fileCount; }
        public int getComponentCount() { return componentCount; }
        public double getArchitectureHealth() { return architectureHealth; }
        public double getDependencyScore() { return dependencyScore; }
        public int getCycleCount() { return cycleCount; }
        public int getSmellCount() { return smellCount; }
        public PipelineResult getResult() { return result; }
        public Path getWorkDir() { return workDir; }
    }

    private static final Map<String, HistoryEntry> historyStore = new LinkedHashMap<>();

    public static synchronized String recordAnalysis(String projectName, String archiveFileName,
                                                    PipelineResult result, Path workDir) {
        if (result == null) return null;
        String id = "proj-" + System.currentTimeMillis();
        HistoryEntry entry = new HistoryEntry(id, projectName, archiveFileName, result, workDir);

        // Insert at beginning of linked map (newest first)
        Map<String, HistoryEntry> updated = new LinkedHashMap<>();
        updated.put(id, entry);
        for (var e : historyStore.entrySet()) {
            if (!e.getKey().equals(id)) {
                updated.put(e.getKey(), e.getValue());
            }
        }
        historyStore.clear();
        historyStore.putAll(updated);
        return id;
    }

    public static synchronized List<HistoryEntry> getHistory() {
        return new ArrayList<>(historyStore.values());
    }

    public static synchronized HistoryEntry getById(String id) {
        return historyStore.get(id);
    }

    public static synchronized boolean deleteById(String id) {
        return historyStore.remove(id) != null;
    }

    public static synchronized void ensureRecorded(PipelineResult result, Path workDir) {
        if (result == null || result.model == null) return;
        String name = result.model.getProjectName();
        for (HistoryEntry e : historyStore.values()) {
            if (e.getProjectName().equalsIgnoreCase(name)) {
                return; // already recorded
            }
        }
        recordAnalysis(name, name + ".zip", result, workDir);
    }
}
