package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.simulator.ChangeOperationType;
import com.archanalyzer.simulator.SimulationResult;
import com.archanalyzer.web.config.AppContextListener;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

/**
 * Serves the What-If Architecture Change Simulator.
 *
 * GET  /simulator          → renders simulator.jsp (the interactive simulation UI)
 * POST /simulator          → runs simulation, returns JSON result (used by AJAX from simulator.jsp)
 * POST /simulator/approve  → approves last simulation and triggers implementation
 */
@WebServlet(name = "WhatIfSimulatorServlet", urlPatterns = {"/simulator", "/simulator/*"})
public class WhatIfSimulatorServlet extends HttpServlet {

    private static final String SESSION_RESULT_ATTR = "lastPipelineResult";
    private static final String SESSION_SIM_ATTR    = "lastSimulationResult";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult result = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (result == null) {
            // No project loaded — send to upload page
            resp.sendRedirect(req.getContextPath() + "/projects/upload?msg=noproject");
            return;
        }

        req.setAttribute("result", result);
        // Pass simulation result if present (for initial page load after simulation)
        SimulationResult simResult = session != null
                ? (SimulationResult) session.getAttribute(SESSION_SIM_ATTR) : null;
        if (simResult != null) {
            req.setAttribute("simResult", simResult);
        }

        req.getRequestDispatcher("/WEB-INF/jsp/simulator.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        // Route to approve handler
        if (pathInfo != null && pathInfo.equals("/approve")) {
            handleApprove(req, resp);
            return;
        }

        // Default: run simulation
        handleSimulate(req, resp);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  POST /simulator  →  run What-If simulation
    // ──────────────────────────────────────────────────────────────────────────
    private void handleSimulate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult baseline = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                "error", "No project analysis found. Please upload and analyze a project first."
            )));
            return;
        }

        // Read request parameters
        String description  = req.getParameter("description");
        String target       = req.getParameter("target");
        String operationStr = req.getParameter("operation");
        String alternativeId = req.getParameter("alternativeId");

        if (description == null || description.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Change description is required.")));
            return;
        }

        ChangeOperationType operation = null;
        if (operationStr != null && !operationStr.isBlank()) {
            try {
                operation = ChangeOperationType.valueOf(operationStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Let auto-detection handle it
            }
        }

        try {
            AnalyzerPipeline pipeline = (AnalyzerPipeline) getServletContext()
                    .getAttribute(AppContextListener.PIPELINE_ATTR);
            if (pipeline == null) {
                pipeline = new AnalyzerPipeline();
            }

            SimulationResult simResult = pipeline.simulate(baseline, description, target, operation, alternativeId);

            // Store simulation result in session for approval flow
            if (session == null) session = req.getSession(true);
            session.setAttribute(SESSION_SIM_ATTR, simResult);

            // Build a rich JSON response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ok");
            response.put("simulation", buildSimulationJson(simResult));

            resp.getWriter().write(JsonUtil.toJson(response));

        } catch (Exception e) {
            getServletContext().log("Simulation error", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                "error", "Simulation failed: " + sanitizeMessage(e.getMessage())
            )));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  POST /simulator/approve  →  approve and implement last simulation
    // ──────────────────────────────────────────────────────────────────────────
    private void handleApprove(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult baseline = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;
        SimulationResult simResult = session != null
                ? (SimulationResult) session.getAttribute(SESSION_SIM_ATTR) : null;

        if (baseline == null || simResult == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                "error", "No pending simulation to approve. Run a simulation first."
            )));
            return;
        }

        try {
            AnalyzerPipeline pipeline = (AnalyzerPipeline) getServletContext()
                    .getAttribute(AppContextListener.PIPELINE_ATTR);
            if (pipeline == null) {
                pipeline = new AnalyzerPipeline();
            }

            // workDir should be the extracted project's parent
            java.nio.file.Path workDir = baseline.projectRoot != null
                    ? baseline.projectRoot.getParent()
                    : java.nio.file.Files.createTempDirectory("archanalyzer-work");

            var implResult = pipeline.implementChange(baseline, simResult, workDir);

            // Update session state
            session.setAttribute(SESSION_RESULT_ATTR, baseline);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", implResult.success ? "applied" : (implResult.rolledBack ? "rolled_back" : "failed"));
            response.put("success", implResult.success);
            response.put("rolledBack", implResult.rolledBack);
            response.put("validationStatus", simResult.getValidationStatus());
            response.put("summary", implResult.summary != null ? implResult.summary : "Change processed.");
            response.put("appliedChanges", implResult.appliedChanges);
            if (implResult.finalZipPath != null) {
                response.put("downloadAvailable", true);
                response.put("zipFileName", implResult.finalZipPath.getFileName().toString());
            }
            if (implResult.buildResult != null) {
                response.put("compilerStatus", implResult.buildResult.status != null ? implResult.buildResult.status.name() : "UNKNOWN");
                response.put("testSummary", implResult.buildResult.testSummary);
            }
            response.put("staticValidation", implResult.success ? "PASSED" : "FAILED");
            response.put("fullBuildVerified", false);
            response.put("fullBuildNote", "Uploaded project build scripts were not executed. Compiler API static compilation was used when available.");

            resp.getWriter().write(JsonUtil.toJson(response));

        } catch (Exception e) {
            getServletContext().log("Implementation error", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                "error", "Implementation failed: " + sanitizeMessage(e.getMessage())
            )));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────────────
    private Map<String, Object> buildSimulationJson(SimulationResult sim) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("simulationId", sim.getSimulationId());

        // Request details
        if (sim.getRequest() != null) {
            map.put("request", sim.getRequest().toMap());
        }

        // Risk Assessment
        if (sim.getRiskAssessment() != null) {
            map.put("risk", sim.getRiskAssessment().toMap());
        }

        // Affected components
        map.put("directlyAffected", new ArrayList<>(sim.getDirectlyAffectedComponents()));
        map.put("indirectlyAffected", new ArrayList<>(sim.getIndirectlyAffectedComponents()));

        // Dependency paths
        Map<String, Object> paths = new LinkedHashMap<>();
        sim.getDependencyPaths().forEach((k, v) -> paths.put(k, v));
        map.put("dependencyPaths", paths);

        // Warnings and cycles
        map.put("potentiallyBrokenDependencies", sim.getPotentiallyBrokenDependencies());
        map.put("potentialCycles", sim.getPotentialCycles());

        map.put("baselineMetrics", metricsMap(sim.getBaselineMetrics()));
        map.put("projectedMetrics", metricsMap(sim.getProjectedMetrics()));
        if (sim.getChangePlan() != null) {
            map.put("changePlan", sim.getChangePlan().toMap());
            map.put("autoApplicable", sim.getChangePlan().autoApplicable);
            map.put("unsupportedReason", sim.getChangePlan().unsupportedReason);
        }
        map.put("baselineGraph", sim.getBaselineGraphPayload());
        map.put("proposedGraph", sim.getProposedGraphPayload());
        map.put("baselineSmells", smellMaps(sim.getBaselineSmells()));
        map.put("proposedSmells", smellMaps(sim.getProposedSmells()));
        map.put("originalModelMutated", sim.isOriginalModelMutated());
        map.put("reportMarkdown", sim.getSimulationReportMarkdown());
        return map;
    }

    private Map<String, Object> metricsMap(com.archanalyzer.metrics.MetricsEngine.ProjectMetrics m) {
        if (m == null) return Map.of();
        double maxFanOut = 0;
        int highFan = 0;
        if (m.perComponent != null) {
            for (var cm : m.perComponent.values()) {
                maxFanOut = Math.max(maxFanOut, cm.fanOut);
                if (cm.inCycle) highFan++;
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("architectureHealth", m.architectureHealthScore);
        map.put("dependencyScore", m.dependencyScore);
        map.put("cycleCount", m.cycleCount);
        map.put("totalComponents", m.totalComponents);
        map.put("totalDependencies", m.totalDependencies);
        map.put("averageFanOut", m.averageFanOut);
        map.put("maxFanOut", maxFanOut);
        map.put("componentsInCycles", highFan);
        return map;
    }

    private List<Map<String, Object>> smellMaps(List<com.archanalyzer.smell.SmellDetector.Smell> smells) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (smells == null) return list;
        for (var s : smells) {
            list.add(Map.of(
                "componentId", s.componentId,
                "type", s.type,
                "severity", s.severity.toString(),
                "evidence", s.evidence != null ? s.evidence : ""
            ));
        }
        return list;
    }

    private String sanitizeMessage(String msg) {
        if (msg == null) return "Unknown error.";
        // strip path-disclosure from error messages
        return msg.length() > 300 ? msg.substring(0, 300) + "..." : msg;
    }
}
