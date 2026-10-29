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

    public AiAnalysisResult analyzeArchitecture(ProjectModel model, ProjectMetrics metrics,
                                                List<Smell> smells, List<RiskPrediction> risks) {
        if (config == null || !config.isConfigured() || provider == null) {
            return AiAnalysisResult.unconfigured(
                "Generative AI service is currently unconfigured. Local ML risk scoring and rule-based smell detection remain fully operational. Set LLM_API_KEY and LLM_PROVIDER in .env to enable live AI reasoning."
            );
        }

        try {
            String minimalContext = buildMinimalContextJson(model, metrics, smells, risks);
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "Analyze the following Java architectural findings and return the structured JSON report:\n" + minimalContext;

            String rawResponse = provider.generateCompletion(systemPrompt, userPrompt);
            return parseAndValidateResponse(rawResponse, model, smells);
        } catch (AiServiceException e) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(), e.getMessage());
        } catch (Exception e) {
            return AiAnalysisResult.failed(provider.getProviderName(), provider.getModelName(),
                "Failed to parse AI response: " + e.getMessage());
        }
    }

    private String buildSystemPrompt() {
        return """
            You are an expert Java Software Architect.
            You explain static analysis findings and suggest safe refactoring recommendations based STRICTLY on provided metrics and smells.

            CRITICAL CONSTRAINTS:
            1. Base every statement strictly on the provided evidence.
            2. Never invent classes, packages, methods, or metrics that are not listed in the input.
            3. In 'explanations', explain why each detected architectural smell matters in simple language and suggest remedies.
            4. In 'recommendations', provide concrete refactorings. Note that only 'Interface Extraction' is currently automated by the engine; mark all other strategies (e.g. Dependency Inversion, Facade Introduction) as engineSupported=false.
            5. Return ONLY a valid JSON object matching this exact schema:
            {
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

        return AiAnalysisResult.success(provider.getProviderName(), provider.getModelName(),
            explanations, recommendations);
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
