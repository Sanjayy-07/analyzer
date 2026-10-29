package com.archanalyzer.simulator;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.simulator.RiskScoringEngine.RiskAssessment;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.util.*;

/**
 * Encapsulates the complete findings of a What-If Architecture Change Simulation.
 */
public class SimulationResult {

    private final String simulationId;
    private final ChangeRequest request;
    private RiskAssessment riskAssessment;
    private final Set<String> directlyAffectedComponents = new LinkedHashSet<>();
    private final Set<String> indirectlyAffectedComponents = new LinkedHashSet<>();
    private final Map<String, List<String>> dependencyPaths = new LinkedHashMap<>();
    private final List<String> potentiallyBrokenDependencies = new ArrayList<>();
    private final List<String> potentialCycles = new ArrayList<>();
    private ProjectMetrics baselineMetrics;
    private ProjectMetrics projectedMetrics;
    private boolean approved = false;
    private boolean applied = false;
    private boolean rolledBack = false;
    private String validationStatus = "NOT_RUN"; // NOT_RUN, PASS, FAIL, WARNING, ROLLED_BACK
    private String deliverableZipName;
    private String simulationReportMarkdown;
    private ArchitectureChangePlan changePlan;
    private DependencyGraph baselineGraph;
    private DependencyGraph proposedGraph;
    private ProjectMetrics proposedMetrics;
    private List<Smell> baselineSmells = new ArrayList<>();
    private List<Smell> proposedSmells = new ArrayList<>();
    private Map<String, Object> baselineGraphPayload;
    private Map<String, Object> proposedGraphPayload;
    private String selectedAlternativeId;
    private boolean originalModelMutated = false;

    public SimulationResult(String simulationId, ChangeRequest request) {
        this.simulationId = simulationId;
        this.request = request;
    }

    public String getSimulationId() { return simulationId; }
    public ChangeRequest getRequest() { return request; }

    public RiskAssessment getRiskAssessment() { return riskAssessment; }
    public void setRiskAssessment(RiskAssessment riskAssessment) { this.riskAssessment = riskAssessment; }

    public Set<String> getDirectlyAffectedComponents() { return directlyAffectedComponents; }
    public Set<String> getIndirectlyAffectedComponents() { return indirectlyAffectedComponents; }
    public Map<String, List<String>> getDependencyPaths() { return dependencyPaths; }

    public List<String> getPotentiallyBrokenDependencies() { return potentiallyBrokenDependencies; }
    public List<String> getPotentialCycles() { return potentialCycles; }

    public ProjectMetrics getBaselineMetrics() { return baselineMetrics; }
    public void setBaselineMetrics(ProjectMetrics baselineMetrics) { this.baselineMetrics = baselineMetrics; }

    public ProjectMetrics getProjectedMetrics() { return projectedMetrics; }
    public void setProjectedMetrics(ProjectMetrics projectedMetrics) { this.projectedMetrics = projectedMetrics; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }

    public boolean isRolledBack() { return rolledBack; }
    public void setRolledBack(boolean rolledBack) { this.rolledBack = rolledBack; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getDeliverableZipName() { return deliverableZipName; }
    public void setDeliverableZipName(String deliverableZipName) { this.deliverableZipName = deliverableZipName; }

    public String getSimulationReportMarkdown() { return simulationReportMarkdown; }
    public void setSimulationReportMarkdown(String markdown) { this.simulationReportMarkdown = markdown; }

    public ArchitectureChangePlan getChangePlan() { return changePlan; }
    public void setChangePlan(ArchitectureChangePlan changePlan) { this.changePlan = changePlan; }

    public DependencyGraph getBaselineGraph() { return baselineGraph; }
    public void setBaselineGraph(DependencyGraph baselineGraph) { this.baselineGraph = baselineGraph; }

    public DependencyGraph getProposedGraph() { return proposedGraph; }
    public void setProposedGraph(DependencyGraph proposedGraph) { this.proposedGraph = proposedGraph; }

    public ProjectMetrics getProposedMetrics() { return proposedMetrics; }
    public void setProposedMetrics(ProjectMetrics proposedMetrics) {
        this.proposedMetrics = proposedMetrics;
        this.projectedMetrics = proposedMetrics;
    }

    public List<Smell> getBaselineSmells() { return baselineSmells; }
    public void setBaselineSmells(List<Smell> baselineSmells) { this.baselineSmells = baselineSmells != null ? baselineSmells : new ArrayList<>(); }

    public List<Smell> getProposedSmells() { return proposedSmells; }
    public void setProposedSmells(List<Smell> proposedSmells) { this.proposedSmells = proposedSmells != null ? proposedSmells : new ArrayList<>(); }

    public Map<String, Object> getBaselineGraphPayload() { return baselineGraphPayload; }
    public void setBaselineGraphPayload(Map<String, Object> baselineGraphPayload) { this.baselineGraphPayload = baselineGraphPayload; }

    public Map<String, Object> getProposedGraphPayload() { return proposedGraphPayload; }
    public void setProposedGraphPayload(Map<String, Object> proposedGraphPayload) { this.proposedGraphPayload = proposedGraphPayload; }

    public String getSelectedAlternativeId() { return selectedAlternativeId; }
    public void setSelectedAlternativeId(String selectedAlternativeId) { this.selectedAlternativeId = selectedAlternativeId; }

    public boolean isOriginalModelMutated() { return originalModelMutated; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("simulationId", simulationId);
        map.put("request", request != null ? request.toMap() : null);
        map.put("risk", riskAssessment != null ? riskAssessment.toMap() : null);
        map.put("directlyAffectedComponents", directlyAffectedComponents);
        map.put("indirectlyAffectedComponents", indirectlyAffectedComponents);
        map.put("dependencyPaths", dependencyPaths);
        map.put("potentiallyBrokenDependencies", potentiallyBrokenDependencies);
        map.put("potentialCycles", potentialCycles);

        if (baselineMetrics != null) {
            map.put("baselineMetrics", Map.of(
                "architectureHealth", baselineMetrics.architectureHealthScore,
                "dependencyScore", baselineMetrics.dependencyScore,
                "cycleCount", baselineMetrics.cycleCount,
                "totalComponents", baselineMetrics.totalComponents,
                "totalDependencies", baselineMetrics.totalDependencies
            ));
        }
        if (projectedMetrics != null) {
            map.put("projectedMetrics", Map.of(
                "architectureHealth", projectedMetrics.architectureHealthScore,
                "dependencyScore", projectedMetrics.dependencyScore,
                "cycleCount", projectedMetrics.cycleCount,
                "totalComponents", projectedMetrics.totalComponents,
                "totalDependencies", projectedMetrics.totalDependencies
            ));
        }

        map.put("approved", approved);
        map.put("applied", applied);
        map.put("rolledBack", rolledBack);
        map.put("validationStatus", validationStatus);
        map.put("deliverableZipName", deliverableZipName);
        map.put("changePlan", changePlan != null ? changePlan.toMap() : null);
        map.put("baselineGraph", baselineGraphPayload);
        map.put("proposedGraph", proposedGraphPayload);
        map.put("originalModelMutated", originalModelMutated);
        return map;
    }
}
