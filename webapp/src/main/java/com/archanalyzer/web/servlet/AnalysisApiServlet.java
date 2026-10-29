package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

/**
 * PART 29: REST-style endpoints. This build's simplified scope note: these
 * endpoints operate on the MOST RECENT analysis stored in the user's HTTP
 * session (set by UploadServlet), not a persisted-by-id lookup across all
 * of a user's historical projects. Wiring these to ProjectDao/AnalysisEntity
 * (Module 7 JPA repositories already defined) to support GET
 * /api/projects/{id}/... for ANY past project id is a documented,
 * straightforward extension (see README "Known Limitations") — the session-
 * scoped version here keeps the demo runnable without requiring a live
 * MySQL instance in front of every code reviewer.
 *
 * Routes handled (relative to /api/projects/*):
 *   GET /api/projects/current/metrics
 *   GET /api/projects/current/graph
 *   GET /api/projects/current/dependencies
 *   GET /api/projects/current/smells
 *   GET /api/projects/current/risk
 *   GET /api/projects/current/candidates
 *   GET /api/projects/current/report
 */
@WebServlet(name = "AnalysisApiServlet", urlPatterns = "/api/projects/*")
public class AnalysisApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult result = session != null
            ? (AnalyzerPipeline.PipelineResult) session.getAttribute("lastPipelineResult") : null;

        if (result == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "No analysis found in the current session.")));
            return;
        }

        String pathInfo = req.getPathInfo(); // e.g. "/current/metrics"
        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing resource path.")));
            return;
        }

        String[] parts = pathInfo.split("/");
        // pathInfo is like "/current/metrics" or "/current/ai/explanations"
        // parts[0]="" parts[1]="current" parts[2]="metrics|ai|..." parts[3]=subresource
        String resource = parts.length >= 3 ? parts[2] : "";
        String subresource = parts.length >= 4 ? parts[3] : "";

        Object payload = switch (resource) {
            case "metrics" -> Map.of(
                "architectureHealth", result.metricsBefore.architectureHealthScore,
                "dependencyScore", result.metricsBefore.dependencyScore,
                "cycleCount", result.metricsBefore.cycleCount,
                "totalComponents", result.metricsBefore.totalComponents,
                "totalDependencies", result.metricsBefore.totalDependencies
            );
            case "graph" -> com.archanalyzer.graph.GraphPayload.from(
                result.model, result.graph, result.metricsBefore, result.smells, result.riskPredictions,
                Set.of(), Set.of(), Set.of());
            case "dependencies" -> result.model.getDependencies().stream()
                .map(d -> (Object) Map.of("from", d.getFrom(), "to", d.getTo(), "type", d.getType().toString()))
                .toList();
            case "smells" -> result.smells.stream()
                .map(s -> (Object) Map.of("component", s.componentId, "type", s.type,
                    "severity", s.severity.toString(), "evidence", s.evidence))
                .toList();
            case "risk" -> result.riskPredictions.stream()
                .map(p -> (Object) Map.of("component", p.componentId, "risk", p.riskLabel,
                    "confidence", p.confidence, "model", p.modelUsed, "factors", p.topContributingFactors))
                .toList();
            case "candidates" -> result.rankedCandidates.stream()
                .map(c -> (Object) Map.of("id", c.id, "strategy", c.strategyName, "target", c.targetComponentId,
                    "score", c.compositeScore, "status", c.status, "projectedDependencyScore", c.projectedDependencyScore))
                .toList();
            case "report" -> Map.of("markdown", result.reportMarkdown);
            case "ai" -> {
                if (result.aiResult == null) {
                    yield Map.of("status", "UNCONFIGURED", "message", "AI analysis result is not available in the current session.");
                }
                if ("explanations".equalsIgnoreCase(subresource)) {
                    yield result.aiResult.getExplanations().stream()
                        .map(e -> (Object) Map.of(
                            "smellType", e.getSmellType(),
                            "componentId", e.getComponentId(),
                            "evidence", e.getEvidence(),
                            "explanation", e.getPlainLanguageExplanation(),
                            "whyItMatters", e.getWhyItMatters(),
                            "maintainabilityImpact", e.getMaintainabilityImpact(),
                            "recommendedAction", e.getRecommendedAction(),
                            "priority", e.getPriority(),
                            "confidence", e.getConfidence()
                        )).toList();
                } else if ("recommendations".equalsIgnoreCase(subresource)) {
                    yield result.aiResult.getRecommendations().stream()
                        .map(r -> (Object) Map.of(
                            "id", r.getId(),
                            "targetComponentId", r.getTargetComponentId(),
                            "detectedProblem", r.getDetectedProblem(),
                            "proposedRefactoring", r.getProposedRefactoring(),
                            "whyItHelps", r.getWhyItHelps(),
                            "expectedBenefits", r.getExpectedBenefits(),
                            "potentialRisks", r.getPotentialRisks(),
                            "engineSupported", r.isEngineSupported(),
                            "implementationPlan", r.getImplementationPlan(),
                            "validationRequirements", r.getValidationRequirements()
                        )).toList();
                } else {
                    yield Map.of(
                        "status", result.aiResult.getStatus().name(),
                        "provider", result.aiResult.getProvider(),
                        "model", result.aiResult.getModel(),
                        "message", result.aiResult.getMessage(),
                        "explanationCount", result.aiResult.getExplanations().size(),
                        "recommendationCount", result.aiResult.getRecommendations().size()
                    );
                }
            }
            default -> null;
        };

        if (payload == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Unknown resource: " + resource)));
            return;
        }
        resp.getWriter().write(JsonUtil.toJson(payload));
    }
}
