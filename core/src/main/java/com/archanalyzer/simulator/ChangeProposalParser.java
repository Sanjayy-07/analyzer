package com.archanalyzer.simulator;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;
import com.archanalyzer.model.ProjectModel.DependencyEdge;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates natural-language change requests into structured, validated ChangeRequests
 * grounded directly in the active ProjectModel's discovered architecture.
 */
public class ChangeProposalParser {

    private static final Pattern BETWEEN = Pattern.compile(
        "between\\s+([A-Za-z][A-Za-z0-9_]*)\\s+and\\s+([A-Za-z][A-Za-z0-9_]*)",
        Pattern.CASE_INSENSITIVE);

    public ChangeRequest parse(String naturalLanguagePrompt,
                               String explicitTargetComponent,
                               ChangeOperationType explicitOperation,
                               ProjectModel model) {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        String projectName = model != null ? model.getProjectName() : "project";
        ChangeRequest req = new ChangeRequest(requestId, projectName, naturalLanguagePrompt);

        if (naturalLanguagePrompt == null || naturalLanguagePrompt.isBlank()) {
            req.setStructuredSummary("Empty change request");
            req.setOperation(ChangeOperationType.OTHER);
            req.setConfidence(0.0);
            req.getUnresolvedQuestions().add("Please describe the proposed architecture change.");
            return req;
        }

        String promptLower = naturalLanguagePrompt.toLowerCase();

        ChangeOperationType op = explicitOperation;
        if (op == null) {
            op = inferOperation(promptLower);
        }
        req.setOperation(op);

        ComponentNode target = resolveExplicit(explicitTargetComponent, model);
        List<ComponentNode> named = findNamedComponents(naturalLanguagePrompt, model);

        if (target == null && named.size() == 1) {
            target = named.get(0);
            req.setConfidence(0.90);
        } else if (target == null) {
            List<ComponentNode> matches = findMatchingComponents(promptLower, model);
            if (matches.size() == 1) {
                target = matches.get(0);
                req.setConfidence(0.90);
            } else if (matches.size() > 1 && named.size() < 2) {
                req.setAmbiguous(true);
                for (ComponentNode m : matches) {
                    req.getCandidateMatches().add(m.getId());
                }
                target = matches.get(0);
                req.setConfidence(0.60);
                req.getUnresolvedQuestions().add(
                    "Target is ambiguous: multiple components matched (" +
                    String.join(", ", req.getCandidateMatches()) + "). Please confirm the intended component."
                );
            }
        }

        ComponentNode secondary = null;
        if (named.size() >= 2) {
            ComponentNode a = named.get(0);
            ComponentNode b = named.get(1);
            ComponentPair pair = orderClientProvider(a, b, model);
            target = pair.client;
            secondary = pair.provider;
            req.setConfidence(Math.max(req.getConfidence(), 0.92));
            req.setAmbiguous(false);
            req.getUnresolvedQuestions().removeIf(q -> q.startsWith("Target is ambiguous"));
        } else if (target != null && named.size() == 1 && !named.get(0).getId().equals(target.getId())) {
            secondary = named.get(0);
        }

        if (target != null) {
            req.setTargetComponentId(target.getId());
            if (target.getDeclaredInFile() != null) {
                req.getAffectedFiles().add(target.getDeclaredInFile());
            }
        } else if (op != ChangeOperationType.ADD_MODULE && op != ChangeOperationType.ADD_COMPONENT) {
            req.setConfidence(Math.min(req.getConfidence(), 0.50));
            req.getUnresolvedQuestions().add("No exact component matched the description. Please select a component from the architecture explorer.");
        }

        if (secondary != null) {
            req.setSecondaryComponentId(secondary.getId());
            if (secondary.getDeclaredInFile() != null) {
                req.getAffectedFiles().add(secondary.getDeclaredInFile());
            }
        }

        synthesizeChangeDetails(req, promptLower, target, secondary, model);
        return req;
    }

    private ComponentNode resolveExplicit(String explicitTargetComponent, ProjectModel model) {
        if (explicitTargetComponent == null || explicitTargetComponent.isBlank() || model == null) {
            return null;
        }
        ComponentNode target = model.getComponent(explicitTargetComponent).orElse(null);
        if (target != null) return target;
        for (ComponentNode cn : model.getComponents()) {
            if (cn.getSimpleName().equalsIgnoreCase(explicitTargetComponent) ||
                cn.getId().equalsIgnoreCase(explicitTargetComponent)) {
                return cn;
            }
        }
        return null;
    }

    private ChangeOperationType inferOperation(String promptLower) {
        if (promptLower.contains("database") || promptLower.contains("entity") ||
            promptLower.contains("field") || promptLower.contains("column") || promptLower.contains("table")) {
            return ChangeOperationType.MODIFY_DATABASE_ENTITY;
        }
        if (promptLower.contains("interface") &&
            (promptLower.contains("between") || promptLower.contains("introduce") ||
             promptLower.contains("extract") || promptLower.contains("coupling") ||
             promptLower.contains("invert"))) {
            return ChangeOperationType.INTRODUCE_INTERFACE;
        }
        if (promptLower.contains("extract interface") || promptLower.contains("invert dependency")
            || promptLower.contains("dependency inversion")) {
            return ChangeOperationType.INTRODUCE_INTERFACE;
        }
        if (promptLower.contains("redirect") && promptLower.contains("depend")) {
            return ChangeOperationType.REDIRECT_DEPENDENCY;
        }
        if ((promptLower.contains("remove the dependency") || promptLower.contains("remove dependency")
            || promptLower.contains("decouple")) && promptLower.contains("between")) {
            return ChangeOperationType.REMOVE_DEPENDENCY;
        }
        if (promptLower.contains("add a dependency") || promptLower.contains("add dependency")) {
            return ChangeOperationType.ADD_DEPENDENCY;
        }
        if (promptLower.contains("add") || promptLower.contains("introduce") ||
            promptLower.contains("implement") || promptLower.contains("new ") ||
            promptLower.contains("create")) {
            if ((promptLower.contains("module") || promptLower.contains("package")) &&
                !promptLower.contains("functionality") && !promptLower.contains("feature") &&
                !promptLower.contains(" to the ") && !promptLower.contains(" into the ")) {
                return ChangeOperationType.ADD_MODULE;
            }
            return ChangeOperationType.ADD_FEATURE;
        }
        if (promptLower.contains("remove") || promptLower.contains("delete") ||
            promptLower.contains("drop") || promptLower.contains("strip")) {
            if (promptLower.contains("api") || promptLower.contains("endpoint") || promptLower.contains("route")) {
                return ChangeOperationType.REMOVE_API;
            }
            return ChangeOperationType.REMOVE_FEATURE;
        }
        if (promptLower.contains("api") || promptLower.contains("endpoint") || promptLower.contains("response structure")) {
            return ChangeOperationType.MODIFY_API;
        }
        if (promptLower.contains("refactor") || promptLower.contains("extract interface") ||
            promptLower.contains("invert dependency")) {
            return ChangeOperationType.REFACTOR_COMPONENT;
        }
        if (promptLower.contains("modify") || promptLower.contains("change") ||
            promptLower.contains("update") || promptLower.contains("replace")) {
            return ChangeOperationType.MODIFY_FEATURE;
        }
        return ChangeOperationType.OTHER;
    }

    private List<ComponentNode> findNamedComponents(String prompt, ProjectModel model) {
        if (model == null || prompt == null) return List.of();
        List<ComponentNode> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Matcher between = BETWEEN.matcher(prompt);
        if (between.find()) {
            addIfPresent(model, between.group(1), found, seen);
            addIfPresent(model, between.group(2), found, seen);
        }

        List<ComponentNode> byLength = new ArrayList<>(model.getComponents());
        byLength.sort((a, b) -> Integer.compare(b.getSimpleName().length(), a.getSimpleName().length()));
        String remaining = prompt;
        for (ComponentNode cn : byLength) {
            String simple = cn.getSimpleName();
            if (simple.length() < 3) continue;
            Pattern p = Pattern.compile("\\b" + Pattern.quote(simple) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(remaining).find() && seen.add(cn.getId())) {
                found.add(cn);
            }
        }
        return found;
    }

    private void addIfPresent(ProjectModel model, String simple, List<ComponentNode> found, Set<String> seen) {
        for (ComponentNode cn : model.getComponents()) {
            if (cn.getSimpleName().equalsIgnoreCase(simple) && seen.add(cn.getId())) {
                found.add(cn);
            }
        }
    }

    private static class ComponentPair {
        final ComponentNode client;
        final ComponentNode provider;
        ComponentPair(ComponentNode client, ComponentNode provider) {
            this.client = client;
            this.provider = provider;
        }
    }

    private ComponentPair orderClientProvider(ComponentNode a, ComponentNode b, ProjectModel model) {
        if (model != null) {
            boolean aDependsOnB = false;
            boolean bDependsOnA = false;
            for (DependencyEdge e : model.getDependencies()) {
                if (e.getFrom().equals(a.getId()) && e.getTo().equals(b.getId())) aDependsOnB = true;
                if (e.getFrom().equals(b.getId()) && e.getTo().equals(a.getId())) bDependsOnA = true;
            }
            if (aDependsOnB && !bDependsOnA) return new ComponentPair(a, b);
            if (bDependsOnA && !aDependsOnB) return new ComponentPair(b, a);
        }
        return new ComponentPair(a, b);
    }

    private List<ComponentNode> findMatchingComponents(String promptLower, ProjectModel model) {
        Map<ComponentNode, Integer> scores = new HashMap<>();
        if (model == null) return List.of();

        for (ComponentNode cn : model.getComponents()) {
            String simple = cn.getSimpleName().toLowerCase();
            String fq = cn.getId().toLowerCase();
            int score = 0;

            if (promptLower.contains("payment") && (simple.contains("payment") || fq.contains("payment"))) score += 10;
            if (promptLower.contains("refund") && (simple.contains("payment") || simple.contains("order"))) score += 8;
            if (promptLower.contains("order") && (simple.contains("order") || fq.contains("order"))) score += 10;
            if (promptLower.contains("user") && (simple.contains("user") || fq.contains("user"))) score += 10;
            if (promptLower.contains("notification") && (simple.contains("notification") || fq.contains("notification"))) score += 10;
            if (promptLower.contains("inventory") && (simple.contains("inventory") || fq.contains("inventory"))) score += 10;
            if (promptLower.contains("search") && (simple.contains("search") || fq.contains("search"))) score += 10;
            if (promptLower.contains("home") && (simple.contains("home") || simple.contains("main") || simple.contains("app"))) score += 8;
            if (promptLower.contains("report") && (simple.contains("report") || fq.contains("report"))) score += 10;

            String base = simple.replaceAll("service|controller|repository|entity|model|component", "");
            if (!base.isEmpty() && promptLower.contains(base)) {
                score += 15;
            }

            if (score > 0) {
                scores.put(cn, score);
            }
        }

        List<ComponentNode> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> Integer.compare(scores.get(b), scores.get(a)));
        return sorted;
    }

    private void synthesizeChangeDetails(ChangeRequest req, String promptLower, ComponentNode target,
                                         ComponentNode secondary, ProjectModel model) {
        String targetName = target != null ? target.getSimpleName() : "Target Component";
        String secondaryName = secondary != null ? secondary.getSimpleName() : null;

        switch (req.getOperation()) {
            case INTRODUCE_INTERFACE -> {
                if (target == null || secondary == null) {
                    req.setStructuredSummary("Introduce an interface to reduce coupling (targets unresolved)");
                    req.setConfidence(Math.min(req.getConfidence(), 0.45));
                    req.getUnresolvedQuestions().add(
                        "Name both components, for example: “introduce an interface between OrderService and PaymentService”.");
                    req.getProposedOperations().add("Cannot build a structural plan until both components are identified.");
                    req.getValidationPlan().add("Re-run simulation after selecting both components.");
                    return;
                }
                String ifaceSimple = "I" + secondary.getSimpleName();
                String pkg = secondary.getPackageName() != null ? secondary.getPackageName() : "";
                String ifaceId = pkg.isBlank() ? ifaceSimple : pkg + "." + ifaceSimple;
                req.setNewComponentId(ifaceId);
                req.setStructuredSummary("Introduce " + ifaceSimple + " between " + targetName + " and " + secondaryName
                    + " to invert the direct dependency.");
                req.getProposedOperations().add("Create interface " + ifaceSimple + " from public methods of " + secondaryName);
                req.getProposedOperations().add("Make " + secondaryName + " implement " + ifaceSimple);
                req.getProposedOperations().add("Retarget " + targetName + " field/parameter types from " + secondaryName + " to " + ifaceSimple);
                req.getAssumptions().add("Client still may instantiate the concrete class; residual instantiation coupling is reported rather than hidden.");
                req.getAssumptions().add("Only statically confirmed edges in the project model are redirected.");
                req.getValidationPlan().add("Recompile " + targetName + ", " + secondaryName + ", and " + ifaceSimple);
                req.getValidationPlan().add("Rebuild the dependency graph and confirm the direct " + targetName + " → " + secondaryName + " type edge is gone");
                req.getValidationPlan().add("Static validation cannot prove runtime behaviour is unchanged");
            }
            case ADD_FEATURE -> {
                if (promptLower.contains("refund") || promptLower.contains("payment")) {
                    req.setStructuredSummary("Add refund functionality to " + targetName);
                    req.getProposedOperations().add("Add method 'void refund(String orderId)' or 'boolean processRefund(String orderId, double amount)' to " + targetName);
                    req.getProposedOperations().add("Update callers (e.g. OrderService) to coordinate order cancellation with refund processing");
                    req.getAssumptions().add("Assumes payment gateway supports synchronous or asynchronous refund callbacks");
                    req.getAssumptions().add("Assumes refund requires a valid orderId and returns processing status");
                    req.getUnresolvedQuestions().add("Should the customer be notified automatically via notification services upon refund completion?");
                    req.getUnresolvedQuestions().add("Should inventory reservations be released when a refund is processed?");
                    req.getValidationPlan().add("Recompile " + targetName + " and direct callers");
                    req.getValidationPlan().add("Run existing test suites (e.g. OrderServiceTest)");
                    req.getValidationPlan().add("Verify no new circular dependency cycles are introduced in the architecture graph");
                } else {
                    req.setStructuredSummary("Add new feature to " + targetName);
                    req.getProposedOperations().add("Implement feature methods and contract signatures in " + targetName);
                    req.getAssumptions().add("Assumes new feature integrates with existing component dependencies");
                    req.getValidationPlan().add("Recompile modified files and verify test suites");
                }
            }
            case REMOVE_FEATURE, REMOVE_DEPENDENCY -> {
                req.setStructuredSummary(req.getOperation() == ChangeOperationType.REMOVE_DEPENDENCY
                    ? "Remove dependency" + (secondaryName != null ? " from " + targetName + " to " + secondaryName : " involving " + targetName)
                    : "Remove functionality from " + targetName);
                req.getProposedOperations().add("Deprecate or remove target methods/handlers in " + targetName);
                req.getProposedOperations().add("Inspect all callers and decouple shared references before deletion");
                req.getAssumptions().add("Removing user-facing feature must not break backend APIs consumed by other services");
                req.getUnresolvedQuestions().add("Are there external API consumers or tests relying on the feature to be removed?");
                req.getValidationPlan().add("Check for broken references across all project components");
                req.getValidationPlan().add("Verify compile check passes with 0 unresolved symbol errors");
            }
            case MODIFY_API -> {
                req.setStructuredSummary("Modify API contract / response structure of " + targetName);
                req.getProposedOperations().add("Update endpoint handler and serialization DTO in " + targetName);
                req.getProposedOperations().add("Update client callers / frontend fetch endpoints to handle revised payload schema");
                req.getAssumptions().add("Assumes backward compatibility or API versioning strategy is defined");
                req.getUnresolvedQuestions().add("Does this change break external third-party API consumers?");
                req.getValidationPlan().add("Run integration tests and verify serialization format");
            }
            case MODIFY_DATABASE_ENTITY -> {
                req.setStructuredSummary("Modify database entity / schema mapping in " + targetName);
                req.getProposedOperations().add("Update field declarations, getters/setters, and JPA/ORM annotations in " + targetName);
                req.getProposedOperations().add("Update repository queries and service mapping layers");
                req.getAssumptions().add("Requires explicit database schema migration (e.g. Flyway / Liquibase / SQL DDL)");
                req.getUnresolvedQuestions().add("Is there historical data in the database that will be lost or requires migration?");
                req.getValidationPlan().add("Verify ORM entity mappings and run repository unit tests");
            }
            case ADD_MODULE, ADD_COMPONENT -> {
                req.setStructuredSummary("Introduce new architectural module into project");
                req.getProposedOperations().add("Create module package structure in service or integration layer");
                req.getProposedOperations().add("Define component interfaces and public contracts");
                req.getAssumptions().add("New module follows layered architecture without introducing reverse dependencies");
                req.getValidationPlan().add("Verify project builds and no circular dependencies are created");
            }
            case ADD_DEPENDENCY -> {
                req.setStructuredSummary("Add dependency" + (secondaryName != null ? " from " + targetName + " to " + secondaryName : " on " + targetName));
                req.getProposedOperations().add("Declare a typed dependency in " + targetName);
                req.getValidationPlan().add("Rebuild graph and inspect new cycles");
            }
            case REDIRECT_DEPENDENCY -> {
                req.setStructuredSummary("Redirect dependency from " + targetName + (secondaryName != null ? " toward " + secondaryName : ""));
                req.getProposedOperations().add("Replace the existing dependency target and update imports/references");
                req.getValidationPlan().add("Rebuild graph; confirm old edge is gone and new edge exists");
            }
            case REFACTOR_COMPONENT -> {
                req.setStructuredSummary("Extract a dedicated interface from " + targetName);
                req.getProposedOperations().add("Generate interface I" + targetName + " from public instance methods");
                req.getProposedOperations().add("Update " + targetName + " to implement the extracted interface");
                req.getValidationPlan().add("Compile extracted interface and implementing class");
            }
            default -> {
                req.setStructuredSummary("Apply architectural change to " + targetName);
                req.getProposedOperations().add("Modify " + targetName + " in accordance with proposed specification");
                req.getValidationPlan().add("Compile and re-analyze architecture graph");
            }
        }
    }
}
