package com.archanalyzer.simulator;

import java.util.*;

/**
 * Structured, model-grounded representation of a proposed architecture change.
 * Simulation applies this plan to a COPY of the dependency graph — never to source files.
 */
public class ArchitectureChangePlan {

    public static class EdgeChange {
        public final String from;
        public final String to;
        public final String type;
        public final String reason;

        public EdgeChange(String from, String to, String type, String reason) {
            this.from = from;
            this.to = to;
            this.type = type != null ? type : "FIELD_TYPE";
            this.reason = reason != null ? reason : "";
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("type", type);
            m.put("reason", reason);
            return m;
        }
    }

    public static class AlternativeDesign {
        public final String id;
        public final String title;
        public final String design;
        public final String benefits;
        public final String disadvantages;
        public final String complexity; // LOW / MEDIUM / HIGH
        public final String predictedRisk;
        public final boolean recommended;
        public final boolean autoApplicable;
        public final ArchitectureChangePlan plan;

        public AlternativeDesign(String id, String title, String design, String benefits,
                                 String disadvantages, String complexity, String predictedRisk,
                                 boolean recommended, boolean autoApplicable, ArchitectureChangePlan plan) {
            this.id = id;
            this.title = title;
            this.design = design;
            this.benefits = benefits;
            this.disadvantages = disadvantages;
            this.complexity = complexity;
            this.predictedRisk = predictedRisk;
            this.recommended = recommended;
            this.autoApplicable = autoApplicable;
            this.plan = plan;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", title);
            m.put("design", design);
            m.put("benefits", benefits);
            m.put("disadvantages", disadvantages);
            m.put("complexity", complexity);
            m.put("predictedRisk", predictedRisk);
            m.put("recommended", recommended);
            m.put("autoApplicable", autoApplicable);
            m.put("affectedComponents", plan != null ? plan.affectedComponents : List.of());
            m.put("addedDependencies", plan != null ? plan.addedDependencies.stream().map(EdgeChange::toMap).toList() : List.of());
            m.put("removedDependencies", plan != null ? plan.removedDependencies.stream().map(EdgeChange::toMap).toList() : List.of());
            return m;
        }
    }

    public static class StructuralRisk {
        public final String title;
        public final String severity; // LOW / MEDIUM / HIGH
        public final List<String> affectedComponents;
        public final String evidence;
        public final String whyItMatters;
        public final String mitigation;
        public final String detectionKind; // DETERMINISTIC / ML / HEURISTIC / UNKNOWN
        public final String confidence;

        public StructuralRisk(String title, String severity, List<String> affectedComponents,
                              String evidence, String whyItMatters, String mitigation,
                              String detectionKind, String confidence) {
            this.title = title;
            this.severity = severity;
            this.affectedComponents = affectedComponents != null ? affectedComponents : List.of();
            this.evidence = evidence;
            this.whyItMatters = whyItMatters;
            this.mitigation = mitigation;
            this.detectionKind = detectionKind;
            this.confidence = confidence;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", title);
            m.put("severity", severity);
            m.put("affectedComponents", affectedComponents);
            m.put("evidence", evidence);
            m.put("whyItMatters", whyItMatters);
            m.put("mitigation", mitigation);
            m.put("detectionKind", detectionKind);
            m.put("confidence", confidence);
            return m;
        }
    }

    public String proposalId;
    public String modelVersion;
    public String interpretedRequirement;
    public ChangeOperationType operation = ChangeOperationType.OTHER;
    public String primaryComponentId;
    public String secondaryComponentId;
    public String newComponentId;
    public final List<String> affectedComponents = new ArrayList<>();
    public final List<EdgeChange> addedDependencies = new ArrayList<>();
    public final List<EdgeChange> removedDependencies = new ArrayList<>();
    public final List<EdgeChange> redirectedDependencies = new ArrayList<>();
    public final List<String> createdComponents = new ArrayList<>();
    public final List<String> deletedComponents = new ArrayList<>();
    public final List<String> plannedSourceChanges = new ArrayList<>();
    public final List<String> assumptions = new ArrayList<>();
    public final List<String> validationRequirements = new ArrayList<>();
    public final List<AlternativeDesign> alternatives = new ArrayList<>();
    public final List<StructuralRisk> risks = new ArrayList<>();
    public boolean autoApplicable;
    public String unsupportedReason;
    public String selectedAlternativeId;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proposalId", proposalId);
        m.put("modelVersion", modelVersion);
        m.put("interpretedRequirement", interpretedRequirement);
        m.put("operation", operation != null ? operation.name() : "OTHER");
        m.put("primaryComponentId", primaryComponentId);
        m.put("secondaryComponentId", secondaryComponentId);
        m.put("newComponentId", newComponentId);
        m.put("affectedComponents", affectedComponents);
        m.put("addedDependencies", addedDependencies.stream().map(EdgeChange::toMap).toList());
        m.put("removedDependencies", removedDependencies.stream().map(EdgeChange::toMap).toList());
        m.put("redirectedDependencies", redirectedDependencies.stream().map(EdgeChange::toMap).toList());
        m.put("createdComponents", createdComponents);
        m.put("deletedComponents", deletedComponents);
        m.put("plannedSourceChanges", plannedSourceChanges);
        m.put("assumptions", assumptions);
        m.put("validationRequirements", validationRequirements);
        m.put("autoApplicable", autoApplicable);
        m.put("unsupportedReason", unsupportedReason);
        m.put("selectedAlternativeId", selectedAlternativeId);
        m.put("alternatives", alternatives.stream().map(AlternativeDesign::toMap).toList());
        m.put("risks", risks.stream().map(StructuralRisk::toMap).toList());
        return m;
    }
}
