package com.archanalyzer.simulator;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.graph.GraphPayload;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPredictionService;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.simulator.ArchitectureChangePlan.AlternativeDesign;
import com.archanalyzer.simulator.ArchitectureChangePlan.EdgeChange;
import com.archanalyzer.simulator.ArchitectureChangePlan.StructuralRisk;
import com.archanalyzer.simulator.RiskScoringEngine.RiskAssessment;
import com.archanalyzer.smell.SmellDetector;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.util.*;

/**
 * Executes What-If Architecture Change Simulations.
 *
 * CRITICAL RULE: Simulation NEVER modifies source files or the baseline graph/model.
 * Structural proposals are applied to an isolated graph copy, then metrics and smells
 * are recalculated from that copy.
 */
public class WhatIfSimulator {

    private final RiskScoringEngine riskEngine = new RiskScoringEngine();
    private final MetricsEngine metricsEngine = new MetricsEngine();
    private final SmellDetector smellDetector = new SmellDetector();

    public SimulationResult simulate(ChangeRequest request,
                                     ProjectModel model,
                                     DependencyGraph graph,
                                     ProjectMetrics baselineMetrics,
                                     RiskPredictionService riskService) {
        return simulate(request, model, graph, baselineMetrics, riskService, null, null);
    }

    public SimulationResult simulate(ChangeRequest request,
                                     ProjectModel model,
                                     DependencyGraph graph,
                                     ProjectMetrics baselineMetrics,
                                     RiskPredictionService riskService,
                                     List<Smell> baselineSmells,
                                     String alternativeId) {
        String simId = "sim-" + UUID.randomUUID().toString().substring(0, 8);
        SimulationResult result = new SimulationResult(simId, request);
        result.setBaselineMetrics(baselineMetrics);
        result.setBaselineGraph(graph);
        result.setSelectedAlternativeId(alternativeId);

        String targetId = resolveVertex(request.getTargetComponentId(), graph);
        if (targetId != null) {
            request.setTargetComponentId(targetId);
        }
        String secondaryId = resolveVertex(request.getSecondaryComponentId(), graph);
        if (secondaryId != null) {
            request.setSecondaryComponentId(secondaryId);
        }

        ArchitectureChangePlan plan = buildPlan(request, model, graph, alternativeId);
        result.setChangePlan(plan);

        Set<String> directImpact = new LinkedHashSet<>();
        Set<String> indirectImpact = new LinkedHashSet<>();
        Map<String, List<String>> paths = new LinkedHashMap<>();
        int maxDepth = 0;

        if (targetId != null && graph.containsVertex(targetId)) {
            directImpact.addAll(graph.incoming(targetId));
            Set<String> allDependents = GraphAlgorithms.dependentsOf(graph, targetId);
            indirectImpact.addAll(allDependents);
            indirectImpact.removeAll(directImpact);
            maxDepth = computePathsAndDepth(graph, targetId, allDependents, paths);
        }

        result.getDirectlyAffectedComponents().addAll(directImpact);
        result.getIndirectlyAffectedComponents().addAll(indirectImpact);
        result.getDependencyPaths().putAll(paths);
        request.getAffectedComponents().addAll(directImpact);
        request.getAffectedComponents().addAll(indirectImpact);

        ChangeOperationType op = request.getOperation() != null ? request.getOperation() : ChangeOperationType.OTHER;
        if (op == ChangeOperationType.REMOVE_FEATURE || op == ChangeOperationType.REMOVE_API
                || op == ChangeOperationType.REMOVE_DEPENDENCY) {
            for (String dep : directImpact) {
                result.getPotentiallyBrokenDependencies().add(
                    String.format("Incoming dependency from '%s' will break if '%s' is removed.", dep, targetId)
                );
            }
        } else if (op == ChangeOperationType.MODIFY_API) {
            for (String dep : directImpact) {
                result.getPotentiallyBrokenDependencies().add(
                    String.format("Consumer '%s' requires contract verification against revised API payload.", dep)
                );
            }
        }

        if (targetId != null && graph.containsVertex(targetId)) {
            List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
            for (Set<String> scc : sccs) {
                if (scc.size() > 1 && scc.contains(targetId)) {
                    result.getPotentialCycles().add(
                        String.format("Target '%s' participates in an existing cycle across %d components: %s",
                            targetId, scc.size(), scc)
                    );
                }
            }
        }

        int baselineEdgeCount = graph.edgeCount();
        DependencyGraph proposed = graph.copy();
        applyPlan(proposed, plan);

        if (graph.edgeCount() != baselineEdgeCount) {
            throw new IllegalStateException("Simulation mutated the original dependency graph.");
        }

        ProjectMetrics projected = metricsEngine.analyze(model, proposed);
        result.setProposedMetrics(projected);
        List<Smell> currentSmells = baselineSmells != null ? baselineSmells : smellDetector.detect(baselineMetrics, graph);
        List<Smell> proposedSmells = smellDetector.detect(projected, proposed);
        result.setBaselineSmells(currentSmells);
        result.setProposedSmells(proposedSmells);

        assessStructuralRisks(plan, graph, proposed, baselineMetrics, projected, request);

        Set<String> addedKeys = new LinkedHashSet<>();
        Set<String> removedKeys = new LinkedHashSet<>();
        for (EdgeChange e : plan.addedDependencies) addedKeys.add(e.from + "->" + e.to);
        for (EdgeChange e : plan.removedDependencies) removedKeys.add(e.from + "->" + e.to);
        for (EdgeChange e : plan.redirectedDependencies) {
            removedKeys.add(e.from + "->" + e.reason); // reason unused; see below
        }
        diffEdges(graph, proposed, addedKeys, removedKeys);

        Set<String> newNodes = new LinkedHashSet<>(plan.createdComponents);
        result.setBaselineGraphPayload(GraphPayload.from(model, graph, baselineMetrics, currentSmells, List.of(), Set.of(), Set.of(), Set.of()));
        result.setProposedGraphPayload(GraphPayload.from(model, proposed, projected, proposedSmells, List.of(), addedKeys, removedKeys, newNodes));
        result.setProposedGraph(proposed);

        detectCycleDelta(graph, proposed, result, plan);

        RiskAssessment assessment = riskEngine.assess(
            request, model, graph, baselineMetrics, directImpact, indirectImpact, maxDepth, riskService
        );
        result.setRiskAssessment(assessment);
        result.setSimulationReportMarkdown(generateReport(result, model));
        return result;
    }

    ArchitectureChangePlan buildPlan(ChangeRequest req, ProjectModel model, DependencyGraph graph, String alternativeId) {
        ArchitectureChangePlan plan = new ArchitectureChangePlan();
        plan.proposalId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        plan.modelVersion = "v" + model.componentCount() + "-" + graph.edgeCount();
        plan.interpretedRequirement = req.getStructuredSummary();
        plan.operation = req.getOperation() != null ? req.getOperation() : ChangeOperationType.OTHER;
        plan.primaryComponentId = req.getTargetComponentId();
        plan.secondaryComponentId = req.getSecondaryComponentId();
        plan.newComponentId = req.getNewComponentId();
        plan.assumptions.addAll(req.getAssumptions());
        plan.validationRequirements.addAll(req.getValidationPlan());
        plan.selectedAlternativeId = alternativeId;

        populatePrimaryPlan(plan, req, graph);
        populateAlternatives(plan, req, graph);

        if (alternativeId != null) {
            for (AlternativeDesign alt : plan.alternatives) {
                if (alternativeId.equals(alt.id) && alt.plan != null) {
                    copyStructural(alt.plan, plan);
                    plan.selectedAlternativeId = alt.id;
                    plan.interpretedRequirement = alt.title + " — " + req.getStructuredSummary();
                    break;
                }
            }
        }
        return plan;
    }

    private void populatePrimaryPlan(ArchitectureChangePlan plan, ChangeRequest req, DependencyGraph graph) {
        ChangeOperationType op = plan.operation;
        String from = plan.primaryComponentId;
        String to = plan.secondaryComponentId;

        switch (op) {
            case INTRODUCE_INTERFACE -> {
                if (from == null || to == null) {
                    plan.autoApplicable = false;
                    plan.unsupportedReason = "Both client and provider components must be identified before an interface can be simulated.";
                    return;
                }
                String iface = req.getNewComponentId() != null ? req.getNewComponentId() : "I" + GraphPayload.shortName(to);
                plan.newComponentId = iface;
                plan.createdComponents.add(iface);
                plan.affectedComponents.addAll(List.of(from, to, iface));
                if (graph.containsVertex(from) && graph.outgoing(from).contains(to)) {
                    plan.removedDependencies.add(new EdgeChange(from, to, "FIELD_TYPE", "Direct coupling replaced by interface"));
                    plan.redirectedDependencies.add(new EdgeChange(from, iface, "FIELD_TYPE", to));
                } else {
                    plan.assumptions.add("No statically confirmed edge from " + from + " to " + to + " was found; the interface is still introduced.");
                }
                plan.addedDependencies.add(new EdgeChange(from, iface, "FIELD_TYPE", "Client depends on abstraction"));
                plan.addedDependencies.add(new EdgeChange(to, iface, "IMPLEMENTS", "Provider implements extracted interface"));
                plan.plannedSourceChanges.add("Create " + iface + " from public methods of " + to);
                plan.plannedSourceChanges.add("Update " + to + " to implement " + iface);
                plan.plannedSourceChanges.add("Retarget typed references in " + from + " from " + to + " to " + iface);
                plan.autoApplicable = true;
                plan.assumptions.add("A residual instantiation of the concrete provider may remain until a factory or DI is introduced.");
            }
            case REFACTOR_COMPONENT -> {
                if (from == null) {
                    plan.autoApplicable = false;
                    plan.unsupportedReason = "No target component for interface extraction.";
                    return;
                }
                String iface = "I" + GraphPayload.shortName(from);
                String pkg = from.contains(".") ? from.substring(0, from.lastIndexOf('.')) : "";
                String ifaceId = pkg.isEmpty() ? iface : pkg + "." + iface;
                plan.newComponentId = ifaceId;
                plan.createdComponents.add(ifaceId);
                plan.affectedComponents.add(from);
                plan.affectedComponents.add(ifaceId);
                plan.addedDependencies.add(new EdgeChange(from, ifaceId, "IMPLEMENTS", "Extracted interface"));
                for (String client : graph.incoming(from)) {
                    plan.removedDependencies.add(new EdgeChange(client, from, "FIELD_TYPE", "Retarget callers to interface"));
                    plan.addedDependencies.add(new EdgeChange(client, ifaceId, "FIELD_TYPE", "Caller uses abstraction"));
                    plan.redirectedDependencies.add(new EdgeChange(client, ifaceId, "FIELD_TYPE", from));
                    plan.affectedComponents.add(client);
                }
                plan.plannedSourceChanges.add("Extract interface " + ifaceId + " from " + from);
                plan.autoApplicable = true;
            }
            case ADD_DEPENDENCY -> {
                if (from == null || to == null) {
                    plan.autoApplicable = false;
                    plan.unsupportedReason = "Add-dependency simulation needs a source and a target component.";
                    return;
                }
                plan.addedDependencies.add(new EdgeChange(from, to, "FIELD_TYPE", "Requested new dependency"));
                plan.affectedComponents.addAll(List.of(from, to));
                plan.autoApplicable = false;
                plan.unsupportedReason = "Adding an arbitrary source dependency is simulated on the graph but not auto-written to source (unsafe without a type-resolved insertion point).";
            }
            case REMOVE_DEPENDENCY -> {
                if (from == null || to == null) {
                    plan.autoApplicable = false;
                    plan.unsupportedReason = "Remove-dependency simulation needs both endpoints.";
                    return;
                }
                plan.removedDependencies.add(new EdgeChange(from, to, "FIELD_TYPE", "Requested decoupling"));
                plan.affectedComponents.addAll(List.of(from, to));
                plan.autoApplicable = false;
                plan.unsupportedReason = "Removing a dependency from source requires rewriting call sites; graph impact is simulated only.";
            }
            case REDIRECT_DEPENDENCY -> {
                if (from == null || to == null) {
                    plan.autoApplicable = false;
                    plan.unsupportedReason = "Redirect simulation needs the current client and the new target.";
                    return;
                }
                for (String existing : graph.outgoing(from)) {
                    if (!existing.equals(to)) {
                        plan.removedDependencies.add(new EdgeChange(from, existing, "FIELD_TYPE", "Redirected away"));
                        plan.redirectedDependencies.add(new EdgeChange(from, to, "FIELD_TYPE", existing));
                    }
                }
                plan.addedDependencies.add(new EdgeChange(from, to, "FIELD_TYPE", "New dependency target"));
                plan.autoApplicable = false;
                plan.unsupportedReason = "Call-site redirection is not auto-applied without type resolution.";
            }
            case ADD_MODULE, ADD_COMPONENT -> {
                String newId = req.getNewComponentId() != null ? req.getNewComponentId()
                    : (modelPackage(from) + ".NewModule");
                if (newId.startsWith(".")) newId = "NewModule";
                plan.newComponentId = newId;
                plan.createdComponents.add(newId);
                if (from != null) {
                    plan.addedDependencies.add(new EdgeChange(from, newId, "FIELD_TYPE", "Existing component uses new module"));
                    plan.affectedComponents.add(from);
                }
                plan.affectedComponents.add(newId);
                plan.autoApplicable = false;
                plan.unsupportedReason = "Creating a new module is simulated structurally; generating a full module skeleton is not auto-applied.";
            }
            case ADD_FEATURE -> {
                plan.affectedComponents.add(from);
                plan.autoApplicable = from != null;
                if (from != null) {
                    plan.plannedSourceChanges.add("Add a feature method to " + from + " without introducing new graph edges.");
                } else {
                    plan.unsupportedReason = "No target component for the feature addition.";
                    plan.autoApplicable = false;
                }
            }
            default -> {
                plan.affectedComponents.add(from);
                plan.autoApplicable = false;
                plan.unsupportedReason = "Operation " + op + " is explained and risk-scored but is not a supported automated source transformation.";
            }
        }
    }

    private void populateAlternatives(ArchitectureChangePlan plan, ChangeRequest req, DependencyGraph graph) {
        if (plan.operation == ChangeOperationType.INTRODUCE_INTERFACE
                && plan.primaryComponentId != null && plan.secondaryComponentId != null) {
            ArchitectureChangePlan dip = new ArchitectureChangePlan();
            copyStructural(plan, dip);
            plan.alternatives.add(new AlternativeDesign(
                "alt-interface",
                "Extract provider interface (dependency inversion)",
                plan.primaryComponentId + " depends on " + plan.newComponentId + "; "
                    + plan.secondaryComponentId + " implements it.",
                "Reduces direct coupling; client can be tested against a stub.",
                "Adds a type and a residual concrete instantiation unless DI is introduced.",
                "LOW",
                "LOW-MEDIUM",
                true,
                true,
                dip
            ));

            ArchitectureChangePlan keepDirect = new ArchitectureChangePlan();
            keepDirect.operation = ChangeOperationType.ADD_FEATURE;
            keepDirect.primaryComponentId = plan.primaryComponentId;
            keepDirect.affectedComponents.add(plan.primaryComponentId);
            keepDirect.plannedSourceChanges.add("Implement the new behaviour directly inside " + plan.primaryComponentId);
            keepDirect.autoApplicable = false;
            keepDirect.unsupportedReason = "Direct in-class implementation does not change the dependency graph.";
            plan.alternatives.add(new AlternativeDesign(
                "alt-inline",
                "Keep the direct dependency and implement the change in the client",
                "No new types. " + plan.primaryComponentId + " continues to depend on " + plan.secondaryComponentId + ".",
                "Smallest code change.",
                "Does not reduce coupling; testing the client still requires the concrete provider.",
                "LOW",
                "MEDIUM",
                false,
                false,
                keepDirect
            ));

            ArchitectureChangePlan events = new ArchitectureChangePlan();
            events.operation = ChangeOperationType.ADD_MODULE;
            String eventBus = modelPackage(plan.primaryComponentId) + ".DomainEventPublisher";
            events.createdComponents.add(eventBus);
            events.removedDependencies.add(new EdgeChange(plan.primaryComponentId, plan.secondaryComponentId, "FIELD_TYPE", "Replace direct call with event"));
            events.addedDependencies.add(new EdgeChange(plan.primaryComponentId, eventBus, "FIELD_TYPE", "Publish events"));
            events.addedDependencies.add(new EdgeChange(plan.secondaryComponentId, eventBus, "FIELD_TYPE", "Subscribe to events"));
            events.autoApplicable = false;
            events.unsupportedReason = "Event-driven rewiring cannot be applied safely from a syntax-only model.";
            plan.alternatives.add(new AlternativeDesign(
                "alt-events",
                "Decouple via a domain event publisher",
                "Replace the direct call with an event. Higher indirection.",
                "Stronger runtime decoupling if many consumers appear later.",
                "More moving parts than this codebase currently needs; not auto-applicable.",
                "HIGH",
                "HIGH",
                false,
                false,
                events
            ));
        } else if (plan.operation == ChangeOperationType.ADD_FEATURE && plan.primaryComponentId != null) {
            ArchitectureChangePlan inline = new ArchitectureChangePlan();
            copyStructural(plan, inline);
            plan.alternatives.add(new AlternativeDesign(
                "alt-inline-feature",
                "Add the behaviour directly on " + GraphPayload.shortName(plan.primaryComponentId),
                "A new method on the existing class. No new module.",
                "Least disruptive; matches a small feature request.",
                "Can grow a god component if repeated.",
                "LOW",
                "LOW",
                true,
                true,
                inline
            ));
            ArchitectureChangePlan extract = new ArchitectureChangePlan();
            String helper = modelPackage(plan.primaryComponentId) + ".FeatureSupport";
            extract.createdComponents.add(helper);
            extract.addedDependencies.add(new EdgeChange(plan.primaryComponentId, helper, "FIELD_TYPE", "Delegate new behaviour"));
            extract.autoApplicable = false;
            extract.unsupportedReason = "Extracting a new collaborator is simulated only.";
            plan.alternatives.add(new AlternativeDesign(
                "alt-extract-feature",
                "Extract the new behaviour into a collaborator",
                "Keeps " + plan.primaryComponentId + " smaller by adding a dedicated type.",
                "Better cohesion if the feature is likely to grow.",
                "Extra type for a possibly one-line change.",
                "MEDIUM",
                "LOW",
                false,
                false,
                extract
            ));
        }
    }

    private void applyPlan(DependencyGraph proposed, ArchitectureChangePlan plan) {
        for (String created : plan.createdComponents) {
            proposed.addVertex(created);
        }
        for (EdgeChange e : plan.removedDependencies) {
            proposed.removeEdge(e.from, e.to);
        }
        for (EdgeChange e : plan.addedDependencies) {
            proposed.addEdge(e.from, e.to);
        }
        for (String deleted : plan.deletedComponents) {
            for (String v : new ArrayList<>(proposed.vertices())) {
                proposed.removeEdge(deleted, v);
                proposed.removeEdge(v, deleted);
            }
        }
    }

    private void diffEdges(DependencyGraph baseline, DependencyGraph proposed,
                           Set<String> addedKeys, Set<String> removedKeys) {
        for (String from : proposed.vertices()) {
            for (String to : proposed.outgoing(from)) {
                if (!baseline.containsVertex(from) || !baseline.outgoing(from).contains(to)) {
                    addedKeys.add(from + "->" + to);
                }
            }
        }
        for (String from : baseline.vertices()) {
            for (String to : baseline.outgoing(from)) {
                if (!proposed.containsVertex(from) || !proposed.outgoing(from).contains(to)) {
                    removedKeys.add(from + "->" + to);
                }
            }
        }
    }

    private void detectCycleDelta(DependencyGraph baseline, DependencyGraph proposed,
                                  SimulationResult result, ArchitectureChangePlan plan) {
        Set<String> before = stringifyCycles(GraphAlgorithms.stronglyConnectedComponents(baseline));
        Set<String> after = stringifyCycles(GraphAlgorithms.stronglyConnectedComponents(proposed));
        for (String c : after) {
            if (!before.contains(c)) {
                result.getPotentialCycles().add("Newly introduced cycle: " + c);
                plan.risks.add(new StructuralRisk(
                    "New circular dependency",
                    "HIGH",
                    List.of(),
                    "Cycle " + c + " appears only in the proposed graph.",
                    "Cycles make independent testing and incremental change harder.",
                    "Redirect one edge through an interface or invert the new dependency.",
                    "DETERMINISTIC",
                    "HIGH"
                ));
            }
        }
        for (String c : before) {
            if (!after.contains(c)) {
                result.getPotentialCycles().add("Cycle eliminated: " + c);
            }
        }
    }

    private Set<String> stringifyCycles(List<Set<String>> sccs) {
        Set<String> out = new LinkedHashSet<>();
        for (Set<String> scc : sccs) {
            if (scc.size() > 1) {
                List<String> ordered = new ArrayList<>(scc);
                Collections.sort(ordered);
                out.add(String.join(" → ", ordered));
            }
        }
        return out;
    }

    private void assessStructuralRisks(ArchitectureChangePlan plan, DependencyGraph baseline,
                                       DependencyGraph proposed, ProjectMetrics before, ProjectMetrics after,
                                       ChangeRequest req) {
        if (after.cycleCount > before.cycleCount) {
            plan.risks.add(new StructuralRisk(
                "Increased circular dependencies",
                "HIGH",
                plan.affectedComponents,
                "Cycle count " + before.cycleCount + " → " + after.cycleCount,
                "New cycles increase change blast radius.",
                "Remove one direction of the new coupling.",
                "DETERMINISTIC",
                "HIGH"
            ));
        }
        if (after.totalDependencies > before.totalDependencies) {
            plan.risks.add(new StructuralRisk(
                "Increased coupling",
                "MEDIUM",
                plan.affectedComponents,
                "Dependency count " + before.totalDependencies + " → " + after.totalDependencies,
                "More edges mean more components must change together.",
                "Prefer depending on an interface rather than adding extra concrete edges.",
                "DETERMINISTIC",
                "HIGH"
            ));
        }
        if (plan.autoApplicable) {
            plan.risks.add(new StructuralRisk(
                "Static simulation cannot prove compilation of every call site",
                "MEDIUM",
                plan.affectedComponents,
                "Graph rewrite is exact; source rewrite coverage depends on the supported transformation.",
                "A cycle-free proposed graph is not the same as a green build.",
                "Approve only after reviewing the source-edit plan, then run static validation.",
                "UNKNOWN",
                "MEDIUM"
            ));
        } else if (plan.unsupportedReason != null) {
            plan.risks.add(new StructuralRisk(
                "Unsupported automated transformation",
                "MEDIUM",
                plan.affectedComponents,
                plan.unsupportedReason,
                "Applying an incomplete rewrite would produce a misleading success state.",
                "Use the simulated graph as a design aid and apply remaining edits manually.",
                "HEURISTIC",
                "HIGH"
            ));
        }
        if (req.isAmbiguous()) {
            plan.risks.add(new StructuralRisk(
                "Ambiguous target",
                "HIGH",
                req.getCandidateMatches(),
                "Multiple components matched the request.",
                "Applying the wrong target would modify unrelated code.",
                "Select the intended component and re-run the simulation.",
                "HEURISTIC",
                "HIGH"
            ));
        }
    }

    private void copyStructural(ArchitectureChangePlan from, ArchitectureChangePlan to) {
        to.operation = from.operation;
        to.primaryComponentId = from.primaryComponentId;
        to.secondaryComponentId = from.secondaryComponentId;
        to.newComponentId = from.newComponentId;
        to.autoApplicable = from.autoApplicable;
        to.unsupportedReason = from.unsupportedReason;
        to.affectedComponents.clear();
        to.affectedComponents.addAll(from.affectedComponents);
        to.addedDependencies.clear();
        to.addedDependencies.addAll(from.addedDependencies);
        to.removedDependencies.clear();
        to.removedDependencies.addAll(from.removedDependencies);
        to.redirectedDependencies.clear();
        to.redirectedDependencies.addAll(from.redirectedDependencies);
        to.createdComponents.clear();
        to.createdComponents.addAll(from.createdComponents);
        to.deletedComponents.clear();
        to.deletedComponents.addAll(from.deletedComponents);
        to.plannedSourceChanges.clear();
        to.plannedSourceChanges.addAll(from.plannedSourceChanges);
    }

    private String resolveVertex(String id, DependencyGraph graph) {
        if (id == null || graph == null) return null;
        if (graph.containsVertex(id)) return id;
        for (String v : graph.vertices()) {
            if (v.equalsIgnoreCase(id) || v.endsWith("." + id) || v.endsWith("/" + id)) {
                return v;
            }
        }
        return id;
    }

    private String modelPackage(String componentId) {
        if (componentId == null) return "";
        int dot = componentId.lastIndexOf('.');
        return dot > 0 ? componentId.substring(0, dot) : "";
    }

    private int computePathsAndDepth(DependencyGraph graph, String target, Set<String> allDependents, Map<String, List<String>> paths) {
        if (allDependents.isEmpty()) return 0;
        Map<String, Integer> depthMap = new HashMap<>();
        depthMap.put(target, 0);

        Deque<String> queue = new ArrayDeque<>();
        queue.add(target);

        Map<String, String> nextOnPath = new HashMap<>();
        int max = 0;

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int d = depthMap.get(curr);

            for (String caller : graph.incoming(curr)) {
                if (!depthMap.containsKey(caller)) {
                    depthMap.put(caller, d + 1);
                    nextOnPath.put(caller, curr);
                    max = Math.max(max, d + 1);
                    queue.add(caller);
                }
            }
        }

        for (String dep : allDependents) {
            List<String> path = new ArrayList<>();
            String step = dep;
            path.add(step);
            while (nextOnPath.containsKey(step)) {
                step = nextOnPath.get(step);
                path.add(step);
            }
            paths.put(dep, path);
        }

        return max;
    }

    private String generateReport(SimulationResult sim, ProjectModel model) {
        StringBuilder sb = new StringBuilder();
        ChangeRequest req = sim.getRequest();
        RiskAssessment risk = sim.getRiskAssessment();
        ArchitectureChangePlan plan = sim.getChangePlan();

        sb.append("# Architecture Change Simulation Report\n\n");
        sb.append("- **Simulation ID**: `").append(sim.getSimulationId()).append("`\n");
        sb.append("- **Project**: `").append(model.getProjectName()).append("`\n");
        sb.append("- **Operation**: `").append(req.getOperation()).append("`\n");
        sb.append("- **Target Component**: `").append(req.getTargetComponentId()).append("`\n");
        if (req.getSecondaryComponentId() != null) {
            sb.append("- **Secondary Component**: `").append(req.getSecondaryComponentId()).append("`\n");
        }
        sb.append("- **Description**: ").append(req.getRawDescription()).append("\n");
        sb.append("- **Original sources modified by simulation**: **no**\n\n");

        if (plan != null) {
            sb.append("## Structured Change Plan\n\n");
            sb.append("- **Summary**: ").append(plan.interpretedRequirement).append("\n");
            sb.append("- **Auto-applicable**: ").append(plan.autoApplicable).append("\n");
            if (plan.unsupportedReason != null) {
                sb.append("- **Limitation**: ").append(plan.unsupportedReason).append("\n");
            }
            sb.append("- **Created components**: ").append(plan.createdComponents).append("\n");
            sb.append("- **Added dependencies**: ").append(plan.addedDependencies.size()).append("\n");
            sb.append("- **Removed dependencies**: ").append(plan.removedDependencies.size()).append("\n\n");
        }

        sb.append("## Risk Assessment\n\n");
        sb.append("- **Overall Risk Score**: **").append(risk.overallScore).append(" / 100** (")
          .append(risk.riskCategory).append(" Risk)\n");
        sb.append("- **Confidence**: ").append(String.format("%.0f%%", risk.confidence * 100)).append("\n");
        if (risk.mlPredictedScore != null) {
            sb.append("- **ML Model Contribution**: ").append(risk.mlPredictedScore).append(" / 100\n");
        }

        sb.append("\n### Contributing Risk Factors\n\n");
        for (String factor : risk.contributingFactors) {
            sb.append("- ").append(factor).append("\n");
        }

        if (plan != null && !plan.risks.isEmpty()) {
            sb.append("\n### Structural Risks\n\n");
            for (StructuralRisk r : plan.risks) {
                sb.append("- **").append(r.title).append("** (").append(r.severity)
                  .append(", ").append(r.detectionKind).append("): ").append(r.evidence).append("\n");
            }
        }

        sb.append("\n## Change Impact Analysis\n\n");
        sb.append("### Directly Affected Components (").append(sim.getDirectlyAffectedComponents().size()).append(")\n\n");
        if (sim.getDirectlyAffectedComponents().isEmpty()) {
            sb.append("_No direct dependents identified._\n\n");
        } else {
            for (String dep : sim.getDirectlyAffectedComponents()) {
                sb.append("- `").append(dep).append("`\n");
            }
            sb.append("\n");
        }

        sb.append("### Indirectly Affected Components (").append(sim.getIndirectlyAffectedComponents().size()).append(")\n\n");
        if (sim.getIndirectlyAffectedComponents().isEmpty()) {
            sb.append("_No indirect dependents identified._\n\n");
        } else {
            for (String dep : sim.getIndirectlyAffectedComponents()) {
                sb.append("- `").append(dep).append("`\n");
            }
            sb.append("\n");
        }

        if (!sim.getDependencyPaths().isEmpty()) {
            sb.append("### Dependency Paths\n\n");
            for (var entry : sim.getDependencyPaths().entrySet()) {
                sb.append("- `").append(String.join(" -> ", entry.getValue())).append("`\n");
            }
            sb.append("\n");
        }

        if (!sim.getPotentiallyBrokenDependencies().isEmpty()) {
            sb.append("### Compatibility Warnings\n\n");
            for (String warn : sim.getPotentiallyBrokenDependencies()) {
                sb.append("> [!WARNING]\n> ").append(warn).append("\n\n");
            }
        }

        sb.append("## Projected Architecture Metrics\n\n");
        if (sim.getBaselineMetrics() != null && sim.getProjectedMetrics() != null) {
            sb.append("| Metric | Current Baseline | Projected After Change |\n");
            sb.append("|---|---|---|\n");
            sb.append("| Architecture Health | ").append(sim.getBaselineMetrics().architectureHealthScore).append(" / 100 | ")
              .append(sim.getProjectedMetrics().architectureHealthScore).append(" / 100 |\n");
            sb.append("| Dependency Pressure | ").append(sim.getBaselineMetrics().dependencyScore).append(" / 100 | ")
              .append(sim.getProjectedMetrics().dependencyScore).append(" / 100 |\n");
            sb.append("| Circular Dependencies | ").append(sim.getBaselineMetrics().cycleCount).append(" | ")
              .append(sim.getProjectedMetrics().cycleCount).append(" |\n");
            sb.append("| Components | ").append(sim.getBaselineMetrics().totalComponents).append(" | ")
              .append(sim.getProjectedMetrics().totalComponents).append(" |\n");
            sb.append("| Dependencies | ").append(sim.getBaselineMetrics().totalDependencies).append(" | ")
              .append(sim.getProjectedMetrics().totalDependencies).append(" |\n\n");
        }

        sb.append("## Assumptions & Limitations\n\n");
        for (String a : req.getAssumptions()) {
            sb.append("- ").append(a).append("\n");
        }
        if (!req.getUnresolvedQuestions().isEmpty()) {
            sb.append("\n### Unresolved Design Decisions\n\n");
            for (String q : req.getUnresolvedQuestions()) {
                sb.append("- ").append(q).append("\n");
            }
        }

        sb.append("\n## Recommended Validation Plan\n\n");
        for (String v : req.getValidationPlan()) {
            sb.append("- ").append(v).append("\n");
        }

        return sb.toString();
    }
}
