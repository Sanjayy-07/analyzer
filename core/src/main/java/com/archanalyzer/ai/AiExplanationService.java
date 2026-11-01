package com.archanalyzer.ai;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.smell.SmellDetector.Smell;

import java.util.*;

/**
 * Orchestrates Generative AI explanations and refactoring recommendations.
 *
 * Adheres strictly to the following architectural rules:
 *  1. Deterministic static analysis, graph algorithms, and local ML remain the source of truth.
 *  2. Minimal context only (no full source code or secrets are sent).
 *  3. LLM output is parsed, validated against the project model, and schema-checked.
 *  4. No hallucinated components are allowed through.
 *  5. Graceful fallback when unconfigured, disabled, or unreachable (never fakes responses).
 */
public class AiExplanationService {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(AiExplanationService.class.getName());

    private final AiConfig config;
    private final LlmProvider provider;

    public AiExplanationService() {
        this(AiConfig.loadFromEnvironment());
    }

    public AiExplanationService(AiConfig config) {
        this.config = config;
        this.provider = resolveProvider(config);
    }

    public AiExplanationService(AiConfig config, LlmProvider provider) {
        this.config = config;
        this.provider = provider;
    }

    private static LlmProvider resolveProvider(AiConfig config) {
        if (!config.isConfigured()) {
            return null;
        }
        return switch (config.getProvider().toLowerCase()) {
            case "gemini" -> new GeminiLlmProvider(config);
            case "openai", "groq", "deepseek", "ollama" -> new OpenAiLlmProvider(config);
            case "anthropic" -> new AnthropicLlmProvider(config);
            case "mock" -> new MockLlmProvider();
            default -> null;
        };
    }

    public static class SynthesizedArchitecture {
        public String pattern = "";
        public String overview = "";
        public List<String> flowSteps = new ArrayList<>();
        public List<String> techStack = new ArrayList<>();
        public String maintainability = "";
    }

    public SynthesizedArchitecture synthesizeArchitecture(ProjectModel model, ProjectMetrics metrics,
                                                          List<Smell> smells, List<RiskPrediction> risks) {
        SynthesizedArchitecture syn = new SynthesizedArchitecture();
        if (model == null) return syn;

        List<ProjectModel.ComponentNode> controllers = new ArrayList<>();
        List<ProjectModel.ComponentNode> services = new ArrayList<>();
        List<ProjectModel.ComponentNode> repositories = new ArrayList<>();
        List<ProjectModel.ComponentNode> models = new ArrayList<>();
        List<ProjectModel.ComponentNode> dtos = new ArrayList<>();
        List<ProjectModel.ComponentNode> configs = new ArrayList<>();

        for (ProjectModel.ComponentNode c : model.getComponents()) {
            String nameLower = c.getSimpleName().toLowerCase();
            String pkgLower = c.getPackageName().toLowerCase();

            Set<String> layers = c.getLayer();
            if (layers.contains("controller") || nameLower.endsWith("controller") || pkgLower.contains(".controller")) {
                controllers.add(c);
            } else if (layers.contains("service") || nameLower.endsWith("service") || pkgLower.contains(".service")) {
                services.add(c);
            } else if (layers.contains("repository") || layers.contains("dao") || nameLower.endsWith("repository") || nameLower.endsWith("dao") || pkgLower.contains(".repository") || pkgLower.contains(".dao")) {
                repositories.add(c);
            } else if (layers.contains("dto") || nameLower.endsWith("dto") || nameLower.endsWith("request") || nameLower.endsWith("response") || pkgLower.contains(".dto")) {
                dtos.add(c);
            } else if (layers.contains("model") || layers.contains("entity") || nameLower.endsWith("entity") || pkgLower.contains(".model") || pkgLower.contains(".entity") || pkgLower.contains(".domain")) {
                models.add(c);
            } else if (nameLower.contains("config") || nameLower.contains("security") || nameLower.contains("jwt") || pkgLower.contains(".config") || pkgLower.contains(".security")) {
                configs.add(c);
            }
        }

        // Check for frontend or non-java assets
        boolean hasFrontend = false;
        if (model.getExcludedLanguages().contains("javascript") || model.getExcludedLanguages().contains("typescript")) {
            hasFrontend = true;
        }
        for (String file : model.getExcludedNonJavaFiles()) {
            String fLower = file.toLowerCase();
            if (fLower.contains("package.json") || fLower.contains("frontend/") || fLower.contains(".jsx") || fLower.contains(".tsx") || fLower.contains("src/app")) {
                hasFrontend = true;
                break;
            }
        }
        if (!hasFrontend) {
            for (ProjectModel.SourceFile sf : model.getFiles()) {
                String l = sf.getLanguage().toLowerCase();
                if ("javascript".equals(l) || "typescript".equals(l)) {
                    hasFrontend = true;
                    break;
                }
            }
        }

        // Detect tech stack
        List<String> tech = new ArrayList<>();
        tech.add("Java (Core Backend Language)");

        boolean isSpring = false;
        boolean isSecurity = false;
        boolean isMongo = false;
        boolean isJpa = false;
        boolean isLombok = false;

        for (ProjectModel.ComponentNode c : model.getComponents()) {
            String p = c.getPackageName().toLowerCase();
            String n = c.getSimpleName().toLowerCase();

            if (p.contains("springframework") || n.endsWith("controller") || n.endsWith("service") || n.endsWith("repository")) {
                isSpring = true;
            }
            if (n.contains("security") || n.contains("jwt") || n.contains("token") || p.contains("security")) {
                isSecurity = true;
            }
            if (n.contains("mongo") || p.contains("mongo")) {
                isMongo = true;
            }
            if (n.contains("jpa") || n.contains("hibernate") || p.contains("jpa") || p.contains("hibernate")) {
                isJpa = true;
            }
        }

        // Check dependencies in model edges
        for (ProjectModel.DependencyEdge edge : model.getDependencies()) {
            String to = edge.getTo().toLowerCase();
            if (to.contains("org.springframework")) isSpring = true;
            if (to.contains("org.springframework.security") || to.contains("jwt")) isSecurity = true;
            if (to.contains("mongodb") || to.contains("mongo")) isMongo = true;
            if (to.contains("persistence") || to.contains("hibernate")) isJpa = true;
            if (to.contains("lombok")) isLombok = true;
        }

        if (isSpring) {
            tech.add("Spring Boot (Modern Java Microservice / Enterprise Framework)");
            tech.add("Spring MVC & REST (HTTP Routing, Controllers & JSON Payloads)");
        }
        if (isSecurity) {
            tech.add("Spring Security & JWT (Stateless Authentication & Role-Based Authorization)");
        }
        if (isMongo) {
            tech.add("MongoDB & Spring Data MongoDB (NoSQL Document Store & Repositories)");
        }
        if (isJpa) {
            tech.add("Spring Data JPA & Hibernate (Object-Relational Persistence)");
        }
        if (isLombok) {
            tech.add("Project Lombok (Annotation-driven Getter/Setter & Builder Generation)");
        }

        String buildTool = model.getBuildInfo().getOrDefault("buildTool", "maven");
        if ("maven".equalsIgnoreCase(buildTool)) {
            tech.add("Apache Maven (Dependency Management & Build Lifecycle)");
        } else if ("gradle".equalsIgnoreCase(buildTool)) {
            tech.add("Gradle (Build Automation System)");
        }

        if (hasFrontend) {
            tech.add("React.js Frontend (Single-Page Interactive User Interface)");
            tech.add("Node.js & npm (Frontend Package & Script Ecosystem)");
        }
        syn.techStack = tech;

        // Determine Pattern
        if (!controllers.isEmpty() && !services.isEmpty() && !repositories.isEmpty()) {
            syn.pattern = hasFrontend
                ? "Full-Stack Client-Server Architecture (React SPA + Spring Boot 3-Tier REST Backend)"
                : "3-Tier Layered Architecture (Controller-Service-Repository Pattern)";
        } else if (!controllers.isEmpty() && !services.isEmpty()) {
            syn.pattern = "Layered Web API Architecture (Controller-Service Pattern)";
        } else if (!services.isEmpty()) {
            syn.pattern = "Service-Oriented Domain Architecture";
        } else {
            syn.pattern = "Modular Component-Based Java Architecture";
        }

        // Extract key business domains
        Set<String> domainSet = new LinkedHashSet<>();
        for (ProjectModel.ComponentNode c : controllers) {
            String n = c.getSimpleName();
            if (n.endsWith("Controller")) n = n.substring(0, n.length() - 10);
            if (!n.isBlank()) domainSet.add(n);
        }
        if (domainSet.isEmpty()) {
            for (ProjectModel.ComponentNode c : services) {
                String n = c.getSimpleName();
                if (n.endsWith("Service")) n = n.substring(0, n.length() - 7);
                if (!n.isBlank()) domainSet.add(n);
            }
        }
        String domainSummary = domainSet.isEmpty()
            ? "core business operations"
            : String.join(", ", domainSet.stream().limit(6).toList()) + (domainSet.size() > 6 ? ", and related workflows" : "");

        // Build Plain-English Overview
        StringBuilder ob = new StringBuilder();
        ob.append("This project follows a ").append(syn.pattern).append(". ");
        ob.append("The codebase is structured to enforce a clean separation of concerns across multiple specialized layers:\n\n");
        ob.append("• Presentation & API Layer (").append(controllers.size()).append(" Controllers): Exposes RESTful endpoints that listen for client actions and receive HTTP JSON requests.\n");
        ob.append("• Business Logic Layer (").append(services.size()).append(" Services): Coordinates core business rules, validations, calculations, and transactional operations.\n");
        ob.append("• Persistence & Data Layer (").append(repositories.size()).append(" Repositories, ").append(models.size()).append(" Entities): Manages querying and storing data records in the database.\n");
        if (!dtos.isEmpty()) {
            ob.append("• Data Transfer Objects (").append(dtos.size()).append(" DTOs): Ensures strong contract isolation so internal database structures are never leaked directly to external consumers.\n");
        }
        ob.append("• Key Business Domains: Implements dedicated modules for ").append(domainSummary).append(".");
        syn.overview = ob.toString();

        // Build 5 Application Flow Steps
        List<String> steps = new ArrayList<>();
        String sampleCtrl = controllers.isEmpty() ? "REST Endpoint" : controllers.get(0).getSimpleName();
        String sampleSvc = services.isEmpty() ? "Service" : services.get(0).getSimpleName();
        String sampleRepo = repositories.isEmpty() ? "Repository" : repositories.get(0).getSimpleName();

        steps.add("Step 1 (Client Request): A user or client application initiates an action (such as logging in, querying a catalog, or submitting an order) by sending an HTTP request.");
        steps.add("Step 2 (Routing & Controller): The request is received by the REST Controller (" + sampleCtrl + "), which verifies authentication tokens, validates input parameters, and delegates to the business layer.");
        steps.add("Step 3 (Business Service Execution): The Service layer (" + sampleSvc + ") executes domain logic, applies business validations, computes calculations, and manages transactions.");
        steps.add("Step 4 (Database Access): The Service communicates with the Repository (" + sampleRepo + ") to query, insert, or update domain records in the database.");
        steps.add("Step 5 (Response Formatting): Processed data is mapped into response DTOs and returned to the client as clean JSON payloads with appropriate HTTP status codes.");
        syn.flowSteps = steps;

        // Build Maintainability Summary
        StringBuilder mb = new StringBuilder();
        if (metrics != null) {
            mb.append("The project exhibits an Architecture Health score of ").append(String.format("%.1f", metrics.architectureHealthScore))
              .append("/100 and a Dependency Pressure of ").append(String.format("%.1f", metrics.dependencyScore)).append("/100. ");
            if (metrics.cycleCount > 0) {
                mb.append("There is ").append(metrics.cycleCount).append(" circular dependency loop detected. In a layered architecture, components in higher layers should depend on lower layers, but never in a loop. Breaking this cycle via Interface Extraction or Dependency Inversion will ensure modules can be tested and modified independently.");
            } else {
                mb.append("No circular dependency loops were detected, indicating a well-isolated directed dependency flow.");
            }
        } else {
            mb.append("Modular structure with clean layer isolation.");
        }
        syn.maintainability = mb.toString();

        return syn;
    }

    public AiAnalysisResult analyzeArchitecture(ProjectModel model, ProjectMetrics metrics,
                                                List<Smell> smells, List<RiskPrediction> risks) {
        SynthesizedArchitecture syn = synthesizeArchitecture(model, metrics, smells, risks);

        if (config == null || !config.isConfigured() || provider == null) {
            return AiAnalysisResult.unconfigured(
                "Generative AI service is currently unconfigured. Local ML risk scoring and rule-based smell detection remain fully operational. Set LLM_API_KEY and LLM_PROVIDER in .env to enable live AI reasoning.",
                syn.pattern, syn.overview, syn.flowSteps, syn.techStack, syn.maintainability
            );
        }

        try {
            String minimalContext = buildMinimalContextJson(model, metrics, smells, risks);
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "Analyze the following Java architectural findings and return the structured JSON report:\n" + minimalContext;

            String rawResponse = provider.generateCompletion(systemPrompt, userPrompt);
            return parseAndValidateResponse(rawResponse, model, smells, syn);
        } catch (AiServiceException e) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(), e.getMessage(),
                syn.pattern, syn.overview, syn.flowSteps, syn.techStack, syn.maintainability);
        } catch (Exception e) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(),
                "Failed to parse AI response: " + e.getMessage(),
                syn.pattern, syn.overview, syn.flowSteps, syn.techStack, syn.maintainability);
        }
    }

    public String askArchitect(String userQuestion, ProjectModel model, ProjectMetrics metrics,
                               List<Smell> smells, List<RiskPrediction> risks) {
        return askArchitect(userQuestion, List.of(), model, metrics, smells, risks).getReply();
    }

    public ArchitectChatResponse askArchitect(String userQuestion, List<ChatMessage> history,
                                             ProjectModel model, ProjectMetrics metrics,
                                             List<Smell> smells, List<RiskPrediction> risks) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return new ArchitectChatResponse("Please ask a question about modifications or refactoring in this project.",
                "OFFLINE", "READY", null);
        }

        if (config == null || !config.isConfigured() || provider == null) {
            LOGGER.info("Generative AI provider is unconfigured. Serving question via offline assistant.");
            String localReply = generateLocalChatResponse(userQuestion, model, metrics, smells, risks);
            return new ArchitectChatResponse(localReply, "OFFLINE", "UNCONFIGURED",
                "Gemini API key is not configured in .env. Running in offline mode.");
        }

        try {
            String systemPrompt = buildChatSystemPrompt(model, metrics, smells, risks);
            String userPrompt = userQuestion.trim();
            String raw = provider.generateChatCompletion(systemPrompt, history, userPrompt);
            if (raw != null && !raw.isBlank()) {
                String liveSource = (provider instanceof GeminiLlmProvider) ? "GEMINI"
                    : (provider.getProviderName() != null && provider.getProviderName().toUpperCase().contains("GEMINI") ? "GEMINI"
                    : (provider.getProviderName() != null ? provider.getProviderName().toUpperCase() : "GEMINI"));
                return new ArchitectChatResponse(raw.trim(), liveSource, "CONNECTED", null);
            }
        } catch (AiServiceException e) {
            String notice = classifyErrorNotice(e.getMessage());
            LOGGER.log(java.util.logging.Level.WARNING, "Gemini API error during chat: " + sanitizeForLog(e.getMessage()));
            String localReply = generateLocalChatResponse(userQuestion, model, metrics, smells, risks);
            return new ArchitectChatResponse(localReply, "OFFLINE", "OFFLINE_FALLBACK", notice);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Unexpected error calling AI provider: " + sanitizeForLog(e.getMessage()));
            String localReply = generateLocalChatResponse(userQuestion, model, metrics, smells, risks);
            return new ArchitectChatResponse(localReply, "OFFLINE", "OFFLINE_FALLBACK",
                "Network or connection error communicating with Gemini API. Switched to offline assistant.");
        }

        String localReply = generateLocalChatResponse(userQuestion, model, metrics, smells, risks);
        return new ArchitectChatResponse(localReply, "OFFLINE", "OFFLINE_FALLBACK", "No response received from Gemini API.");
    }

    private String classifyErrorNotice(String msg) {
        if (msg == null) return "Gemini API call failed. Running in offline fallback mode.";
        String lower = msg.toLowerCase();
        if (lower.contains("authentication") || lower.contains("401") || lower.contains("403")) {
            return "Gemini authentication failed (Invalid API key). Serving response via offline assistant.";
        }
        if (lower.contains("quota") || lower.contains("rate limit") || lower.contains("429")) {
            return "Gemini API rate limit or quota exceeded (HTTP 429). Serving response via offline assistant.";
        }
        if (lower.contains("503") || lower.contains("unavailable") || lower.contains("high demand")) {
            return "Gemini service temporarily experiencing high demand (HTTP 503). Serving response via offline assistant.";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "Gemini API request timed out. Serving response via offline assistant.";
        }
        if (lower.contains("not found") || lower.contains("404")) {
            return "Configured Gemini model was not found (HTTP 404). Serving response via offline assistant.";
        }
        if (lower.contains("empty response") || lower.contains("empty text")) {
            return "Gemini returned an empty response. Serving response via offline assistant.";
        }
        if (lower.contains("network") || lower.contains("connect")) {
            return "Network connection to Gemini API failed. Serving response via offline assistant.";
        }
        return "Gemini API notice: " + sanitizeForLog(msg) + ". Switched to offline assistant.";
    }

    private static String sanitizeForLog(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("(?i)key=[^&\\s]+", "key=[REDACTED]");
    }

    private String buildChatSystemPrompt(ProjectModel model, ProjectMetrics metrics,
                                         List<Smell> smells, List<RiskPrediction> risks) {
        String projectName = model != null ? model.getProjectName() : "Java Project";
        double health = metrics != null ? metrics.architectureHealthScore : 70.0;
        double depScore = metrics != null ? metrics.dependencyScore : 25.0;
        int cycles = metrics != null ? metrics.cycleCount : 0;
        int compCount = model != null ? model.componentCount() : 0;

        StringBuilder smellSummary = new StringBuilder();
        if (smells != null && !smells.isEmpty()) {
            for (int i = 0; i < Math.min(smells.size(), 6); i++) {
                Smell s = smells.get(i);
                smellSummary.append(s.type).append(" in ").append(s.componentId).append("; ");
            }
        } else {
            smellSummary.append("None detected.");
        }

        StringBuilder riskSummary = new StringBuilder();
        if (risks != null) {
            int rCount = 0;
            for (RiskPrediction r : risks) {
                if ("HIGH".equals(r.riskLabel) && rCount < 5) {
                    riskSummary.append(r.componentId).append(" (High Risk); ");
                    rCount++;
                }
            }
        }
        if (riskSummary.length() == 0) riskSummary.append("Standard risk levels across components.");

        return """
            You are an expert Java Software Architect and Assistant for the project "%s".
            You assist developers and students by answering questions about system architecture, code modifications, and refactoring.

            PROJECT ARCHITECTURAL CONTEXT:
            - Total Components: %d
            - Architecture Health: %.1f / 100
            - Dependency Pressure: %.1f / 100
            - Circular Dependency Loops: %d
            - Detected Architectural Smells: %s
            - High-Risk Components: %s

            INSTRUCTIONS:
            1. Answer the user's question directly in simple, clear, and beginner-friendly terms.
            2. Suggest concrete, safe code changes and refactoring steps tailored to this specific Java project.
            3. When discussing circular dependencies or tightly coupled classes, explain how to apply Dependency Inversion or Interface Extraction.
            4. Keep responses structured with short paragraphs or bullet points. Do not include JSON syntax in your chat response.
            """.formatted(projectName, compCount, health, depScore, cycles, smellSummary.toString(), riskSummary.toString());
    }

    private String generateLocalChatResponse(String question, ProjectModel model, ProjectMetrics metrics,
                                            List<Smell> smells, List<RiskPrediction> risks) {
        String q = question.toLowerCase();
        String projectName = model != null ? model.getProjectName() : "the project";

        if (q.contains("cycle") || q.contains("circular") || q.contains("loop")) {
            if (metrics != null && metrics.cycleCount > 0) {
                String smellComp = (smells != null && !smells.isEmpty()) ? smells.get(0).componentId : "the detected components";
                return "The project currently has " + metrics.cycleCount + " circular dependency loop(s), notably involving `" + smellComp + "`.\n\n"
                    + "💡 **Recommended Refactoring Strategy:**\n"
                    + "1. **Extract Interface:** Introduce an interface (e.g., `I" + smellComp + "`) to invert the dependency direction.\n"
                    + "2. **Dependency Inversion Principle (DIP):** Make the caller depend on the abstraction (interface) instead of the concrete implementation.\n"
                    + "3. **Test in Simulator:** Use the **What-If Simulator** on the dashboard to preview how interface extraction eliminates the cycle and boosts your Architecture Health score.";
            } else {
                return "Great news! The static analysis did not detect any circular dependency loops in " + projectName + ". Dependencies follow a clean, directed hierarchy.";
            }
        }

        if (q.contains("modify") || q.contains("modification") || q.contains("change") || q.contains("safe")) {
            return "When planning modifications in **" + projectName + "**:\n\n"
                + "• **Presentation Layer (Controllers):** Safe to add new endpoints or DTO mappings with low blast radius.\n"
                + "• **Business Layer (Services):** Core business logic coordinates multiple layers. Before modifying a service, check its downstream impact in the **What-If Simulator**.\n"
                + "• **Persistence Layer (Repositories):** Keep database queries isolated in repositories so changes don't ripple into controller endpoints.\n"
                + "• **Best Practice:** Keep DTOs separate from database entities so internal schemas aren't exposed externally.";
        }

        if (q.contains("refactor") || q.contains("improve") || q.contains("score") || q.contains("health")) {
            double health = metrics != null ? metrics.architectureHealthScore : 70.0;
            return "Current Architecture Health is **" + String.format("%.1f", health) + "/100**.\n\n"
                + "🔧 **Key Steps to Improve Architecture:**\n"
                + "1. **Decouple Tight Loops:** Invert dependencies using interfaces to break circular references.\n"
                + "2. **Reduce God Classes:** If any class has high fan-out or excessive LOC, split responsibilities into specialized helper services.\n"
                + "3. **Apply What-If Simulator:** Test candidate refactorings in the simulator to verify the projected score improvement before applying changes.";
        }

        return "Here is what the architecture analysis shows for **" + projectName + "**:\n\n"
            + "• **Architecture Health:** " + (metrics != null ? String.format("%.1f", metrics.architectureHealthScore) : "N/A") + "/100\n"
            + "• **Dependency Pressure:** " + (metrics != null ? String.format("%.1f", metrics.dependencyScore) : "N/A") + "/100\n"
            + "• **Cycles Detected:** " + (metrics != null ? metrics.cycleCount : 0) + "\n\n"
            + "You can ask about:\n"
            + "• How to break circular dependencies with interfaces\n"
            + "• How to safely modify specific services or controllers\n"
            + "• Understanding the request flow between layers\n"
            + "• Strategies to boost your project's architecture health score";
    }

    private String buildSystemPrompt() {
        return """
            You are an expert Java Software Architect.
            You explain static analysis findings and suggest safe refactoring recommendations based STRICTLY on provided metrics and smells.

            CRITICAL CONSTRAINTS:
            1. Base every statement strictly on the provided evidence.
            2. Never invent classes, packages, methods, or metrics that are not listed in the input.
            3. In 'architecturePattern', state the high-level pattern (e.g. 3-Tier Layered Architecture / MVC).
            4. In 'plainEnglishOverview', explain the project architecture, separation of concerns, and purpose in simple, clear words for a beginner.
            5. In 'applicationFlowSteps', provide 5 sequential steps explaining how a client request flows through Controllers, Services, Repositories, Database, and back as a response.
            6. In 'detectedTechStack', list key technologies with friendly descriptions.
            7. In 'maintainabilitySummary', summarize code health and modularity in plain language.
            8. In 'explanations', explain why each detected architectural smell matters in simple language and suggest remedies.
            9. In 'recommendations', provide concrete refactorings. Note that only 'Interface Extraction' is currently automated by the engine; mark all other strategies (e.g. Dependency Inversion, Facade Introduction) as engineSupported=false.
            10. Return ONLY a valid JSON object matching this exact schema:
            {
              "architecturePattern": "string",
              "plainEnglishOverview": "string",
              "applicationFlowSteps": [ "string", "string" ],
              "detectedTechStack": [ "string", "string" ],
              "maintainabilitySummary": "string",
              "explanations": [
                {
                  "smellType": "string",
                  "componentId": "string",
                  "evidence": "string",
                  "plainLanguageExplanation": "string",
                  "whyItMatters": "string",
                  "maintainabilityImpact": "string",
                  "recommendedAction": "string",
                  "priority": "HIGH" | "MEDIUM" | "LOW",
                  "confidence": "High" | "Medium"
                }
              ],
              "recommendations": [
                {
                  "id": "rec-1",
                  "targetComponentId": "string",
                  "detectedProblem": "string",
                  "proposedRefactoring": "string",
                  "whyItHelps": "string",
                  "expectedBenefits": "string",
                  "potentialRisks": "string",
                  "preconditions": "string",
                  "engineSupported": true | false,
                  "implementationPlan": "string",
                  "validationRequirements": "string"
                }
              ]
            }
            """;
    }

    private String buildMinimalContextJson(ProjectModel model, ProjectMetrics metrics,
                                          List<Smell> smells, List<RiskPrediction> risks) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"projectName\": \"").append(escape(model.getProjectName())).append("\",\n");
        sb.append("  \"componentCount\": ").append(model.componentCount()).append(",\n");
        sb.append("  \"architectureHealthScore\": ").append(metrics.architectureHealthScore).append(",\n");
        sb.append("  \"dependencyScore\": ").append(metrics.dependencyScore).append(",\n");
        sb.append("  \"cycleCount\": ").append(metrics.cycleCount).append(",\n");

        sb.append("  \"detectedSmells\": [\n");
        for (int i = 0; i < smells.size(); i++) {
            Smell s = smells.get(i);
            sb.append("    {\"type\": \"").append(escape(s.type))
              .append("\", \"componentId\": \"").append(escape(s.componentId))
              .append("\", \"severity\": \"").append(s.severity)
              .append("\", \"evidence\": \"").append(escape(s.evidence)).append("\"}");
            if (i < smells.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"mlRiskPredictions\": [\n");
        int count = 0;
        for (RiskPrediction r : risks) {
            if ("HIGH".equals(r.riskLabel) || "MEDIUM".equals(r.riskLabel) || count < 5) {
                if (count > 0) sb.append(",\n");
                sb.append("    {\"componentId\": \"").append(escape(r.componentId))
                  .append("\", \"risk\": \"").append(r.riskLabel)
                  .append("\", \"confidence\": \"").append(String.format("%.0f%%", r.confidence * 100))
                  .append("\", \"factors\": ").append(escapeArray(r.topContributingFactors)).append("}");
                count++;
            }
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private AiAnalysisResult parseAndValidateResponse(String json, ProjectModel model, List<Smell> smells) {
        return parseAndValidateResponse(json, model, smells, null);
    }

    private AiAnalysisResult parseAndValidateResponse(String json, ProjectModel model, List<Smell> smells,
                                                     SynthesizedArchitecture syn) {
        if (json == null || json.isBlank() || !json.contains("{") || !json.contains("}")) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(),
                "Failed to parse AI response: Response was not valid JSON or was empty");
        }

        int expStart = json.indexOf("\"explanations\"");
        int recStart = json.indexOf("\"recommendations\"");
        if (expStart < 0 && recStart < 0) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(),
                "Failed to parse AI response: Missing required schema fields 'explanations' or 'recommendations'");
        }

        Set<String> knownComponents = new HashSet<>();
        for (var c : model.getComponents()) {
            knownComponents.add(c.getId());
            knownComponents.add(c.getSimpleName());
        }
        for (Smell s : smells) {
            knownComponents.add(s.componentId);
        }

        List<ArchitectureSmellExplanation> explanations = new ArrayList<>();
        List<AiRefactoringRecommendation> recommendations = new ArrayList<>();

        // Parse explanations array
        if (expStart >= 0) {
            int arrayOpen = json.indexOf('[', expStart);
            int arrayClose = findMatchingBracket(json, arrayOpen);
            if (arrayOpen >= 0 && arrayClose > arrayOpen) {
                String expArray = json.substring(arrayOpen + 1, arrayClose);
                parseExplanationObjects(expArray, knownComponents, explanations);
            }
        }

        // Parse recommendations array
        if (recStart >= 0) {
            int arrayOpen = json.indexOf('[', recStart);
            int arrayClose = findMatchingBracket(json, arrayOpen);
            if (arrayOpen >= 0 && arrayClose > arrayOpen) {
                String recArray = json.substring(arrayOpen + 1, arrayClose);
                parseRecommendationObjects(recArray, knownComponents, recommendations);
            }
        }

        String pattern = extractField(json, "architecturePattern");
        if (pattern.isBlank()) pattern = syn != null ? syn.pattern : "";

        String overview = extractField(json, "plainEnglishOverview");
        if (overview.isBlank()) overview = syn != null ? syn.overview : "";

        String maintainability = extractField(json, "maintainabilitySummary");
        if (maintainability.isBlank()) maintainability = syn != null ? syn.maintainability : "";

        List<String> flowSteps = extractStringArray(json, "applicationFlowSteps");
        if (flowSteps.isEmpty() && syn != null) flowSteps = syn.flowSteps;

        List<String> techStack = extractStringArray(json, "detectedTechStack");
        if (techStack.isEmpty() && syn != null) techStack = syn.techStack;

        return AiAnalysisResult.success(provider.getProviderName(), provider.getModelName(),
            explanations, recommendations, pattern, overview, flowSteps, techStack, maintainability);
    }

    private List<String> extractStringArray(String json, String fieldName) {
        List<String> list = new ArrayList<>();
        String pattern = "\"" + fieldName + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return list;
        int arrayOpen = json.indexOf('[', idx + pattern.length());
        if (arrayOpen < 0) return list;
        int arrayClose = findMatchingBracket(json, arrayOpen);
        if (arrayClose <= arrayOpen) return list;
        String content = json.substring(arrayOpen + 1, arrayClose);

        boolean inQuote = false;
        boolean escape = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                if (inQuote) {
                    String str = sb.toString().trim();
                    if (!str.isBlank()) list.add(str);
                    sb.setLength(0);
                    inQuote = false;
                } else {
                    inQuote = true;
                }
            } else if (inQuote) {
                sb.append(c);
            }
        }
        return list;
    }

    private void parseExplanationObjects(String text, Set<String> knownComponents,
                                         List<ArchitectureSmellExplanation> out) {
        List<String> objects = splitJsonObjects(text);
        for (String obj : objects) {
            String smellType = extractField(obj, "smellType");
            String componentId = extractField(obj, "componentId");
            String evidence = extractField(obj, "evidence");
            String plainExp = extractField(obj, "plainLanguageExplanation");
            String why = extractField(obj, "whyItMatters");
            String impact = extractField(obj, "maintainabilityImpact");
            String action = extractField(obj, "recommendedAction");
            String priority = extractField(obj, "priority");
            String confidence = extractField(obj, "confidence");

            // Validation: component must be known
            if (!componentId.isBlank() && isComponentRecognized(componentId, knownComponents)) {
                out.add(new ArchitectureSmellExplanation(
                    smellType, componentId, evidence, plainExp, why, impact, action, priority, confidence
                ));
            }
        }
    }

    private void parseRecommendationObjects(String text, Set<String> knownComponents,
                                            List<AiRefactoringRecommendation> out) {
        List<String> objects = splitJsonObjects(text);
        int counter = 1;
        for (String obj : objects) {
            String id = extractField(obj, "id");
            if (id.isBlank()) id = "ai-rec-" + (counter++);
            String target = extractField(obj, "targetComponentId");
            String problem = extractField(obj, "detectedProblem");
            String proposed = extractField(obj, "proposedRefactoring");
            String why = extractField(obj, "whyItHelps");
            String benefits = extractField(obj, "expectedBenefits");
            String risks = extractField(obj, "potentialRisks");
            String pre = extractField(obj, "preconditions");
            String engineSuppStr = extractField(obj, "engineSupported");
            boolean engineSupported = "true".equalsIgnoreCase(engineSuppStr)
                || proposed.toLowerCase().contains("extract interface");
            String plan = extractField(obj, "implementationPlan");
            String val = extractField(obj, "validationRequirements");

            if (!target.isBlank() && isComponentRecognized(target, knownComponents)) {
                out.add(new AiRefactoringRecommendation(
                    id, target, problem, proposed, why, benefits, risks, pre,
                    engineSupported, plan, val
                ));
            }
        }
    }

    private boolean isComponentRecognized(String name, Set<String> known) {
        if (known.contains(name)) return true;
        for (String k : known) {
            if (k.endsWith("." + name) || name.endsWith("." + k)) return true;
        }
        return false;
    }

    private List<String> splitJsonObjects(String arrayBody) {
        List<String> list = new ArrayList<>();
        int braceDepth = 0;
        int objStart = -1;
        boolean inQuote = false;
        boolean escape = false;

        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (inQuote) continue;

            if (c == '{') {
                if (braceDepth == 0) objStart = i;
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && objStart >= 0) {
                    list.add(arrayBody.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return list;
    }

    private int findMatchingBracket(String s, int openPos) {
        if (openPos < 0 || openPos >= s.length()) return -1;
        char openChar = s.charAt(openPos);
        char closeChar = openChar == '[' ? ']' : '}';
        int depth = 0;
        boolean inQuote = false;
        boolean escape = false;

        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inQuote = !inQuote; continue; }
            if (inQuote) continue;

            if (c == openChar) depth++;
            else if (c == closeChar) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String extractField(String obj, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int idx = obj.indexOf(pattern);
        if (idx < 0) return "";
        int valStart = idx + pattern.length();
        while (valStart < obj.length() && Character.isWhitespace(obj.charAt(valStart))) valStart++;
        if (valStart >= obj.length()) return "";

        char firstChar = obj.charAt(valStart);
        if (firstChar == '"') {
            StringBuilder sb = new StringBuilder();
            boolean escape = false;
            for (int i = valStart + 1; i < obj.length(); i++) {
                char c = obj.charAt(i);
                if (escape) {
                    switch (c) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(c);
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            // boolean, number, etc.
            int end = valStart;
            while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}' && !Character.isWhitespace(obj.charAt(end))) {
                end++;
            }
            return obj.substring(valStart, end).trim();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String escapeArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(escape(list.get(i))).append("\"");
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public AiConfig getConfig() { return config; }
    public LlmProvider getProvider() { return provider; }
}
