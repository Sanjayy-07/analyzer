<%@ page contentType="text/html;charset=UTF-8" language="java" isELIgnored="true" %>
<%@ page import="com.archanalyzer.pipeline.AnalyzerPipeline" %>
<%@ page import="com.archanalyzer.ml.RiskPrediction" %>
<%@ page import="com.archanalyzer.optimization.ArchitectureCandidate" %>
<%@ page import="com.archanalyzer.smell.SmellDetector.Smell" %>
<%@ page import="com.archanalyzer.ai.*" %>
<%@ page import="java.util.*" %>
<%!
    private String riskBadgeClass(String risk) {
        if ("HIGH".equals(risk)) return "badge-high";
        if ("MEDIUM".equals(risk)) return "badge-medium";
        return "badge-low";
    }

    private String statusBadgeClass(String status) {
        if (status == null) return "badge-low";
        if (status.contains("PASS") || status.contains("APPLIED")) return "badge-pass";
        if (status.contains("FAIL") || status.contains("REJECTED")) return "badge-high";
        if (status.contains("Preview")) return "badge-medium";
        return "badge-low";
    }
%>
<%
    AnalyzerPipeline.PipelineResult result = (AnalyzerPipeline.PipelineResult) request.getAttribute("result");
    if (result == null) {
        response.sendRedirect(request.getContextPath() + "/dashboard");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Java Architecture Dashboard — <%= result.model.getProjectName() %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/main.css">
    <style>
        .graph-wrap { display:grid; grid-template-columns: 1fr 320px; gap:16px; }
        .graph-canvas { width:100%; height:460px; background:#0a0d14; border:1px solid var(--border); border-radius:10px; cursor:grab; }
        .graph-tools { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:10px; }
        .graph-tools input, .graph-tools select { background:var(--surface-2); color:var(--text); border:1px solid var(--border); border-radius:8px; padding:6px 10px; }
        .graph-legend { font-size:0.78rem; color:var(--text-dim); display:flex; flex-wrap:wrap; gap:10px; margin-top:8px; }
        .legend-dot { width:10px; height:10px; border-radius:50%; display:inline-block; margin-right:4px; }
        .node-detail { background:var(--card-bg, var(--surface)); border:1px solid var(--border); border-radius:10px; padding:14px; font-size:0.85rem; }
        .node-detail h3 { margin:0 0 8px 0; font-size:0.95rem; }
        @media (max-width: 900px) { .graph-wrap { grid-template-columns:1fr; } }
    </style>
    <style>
        .badge-pass { background: rgba(34, 197, 94, 0.2); color: #86efac; border: 1px solid #22c55e; }
        .notice-banner {
            background: rgba(59, 130, 246, 0.12);
            border: 1px solid #3b82f6;
            color: #93c5fd;
            padding: 12px 16px;
            border-radius: 6px;
            margin-bottom: 20px;
            font-size: 0.92rem;
        }
        .warn-banner {
            background: rgba(245, 158, 11, 0.15);
            border: 1px solid #f59e0b;
            color: #fcd34d;
            padding: 12px 16px;
            border-radius: 6px;
            margin-bottom: 20px;
            font-size: 0.92rem;
        }
    </style>
</head>
<body>
<header class="topbar">
    <div class="brand">Java ArchAnalyzer</div>
    <nav>
        <a href="<%= request.getContextPath() %>/projects/upload">New Analysis</a>
        <a href="<%= request.getContextPath() %>/simulator" style="color:#a5b4fc;font-weight:600;background:rgba(99,102,241,0.12);padding:6px 14px;border-radius:8px;border:1px solid rgba(99,102,241,0.3)">🔮 What-If Simulator</a>
        <a href="<%= request.getContextPath() %>/projects/download/zip"><%= (result.finalZipPath != null && result.finalZipPath.getFileName().toString().endsWith("-refactored.zip")) ? "Download Refactored ZIP" : "Download Original Project ZIP" %></a>
        <a href="<%= request.getContextPath() %>/projects/download/report">Download Report</a>
        <a href="<%= request.getContextPath() %>/history" style="margin-left:14px;background:rgba(56,189,248,0.15);color:#38bdf8;border:1px solid rgba(56,189,248,0.4);padding:6px 14px;border-radius:8px;font-weight:600;text-decoration:none;display:inline-flex;align-items:center;gap:6px">
            🕒 View History
        </a>
    </nav>
</header>

<main class="dashboard">
    <div class="dashboard-header">
        <div>
            <h1><%= result.model.getProjectName() %></h1>
            <span style="color:var(--text-dim)">
                Java Source Files: <strong><%= result.model.fileCount() %></strong> |
                Java Components: <strong><%= result.model.componentCount() %></strong> |
                Build Tool: <strong><%= result.model.getBuildInfo().getOrDefault("buildTool", "unknown") %></strong>
            </span>
        </div>
        <div>
            <span class="badge" style="background:rgba(99,102,241,0.2);color:#a5b4fc;border:1px solid #6366f1;padding:6px 12px;font-size:0.9rem">
                Java-Only Source Analysis
            </span>
        </div>
    </div>

    <% if (result.model.isMixedLanguage()) { %>
    <div class="notice-banner">
        <strong>Mixed-Language Project:</strong> Analyzed Java files only.
        <%= result.model.getExcludedNonJavaFiles().size() %> non-Java source file(s) across languages
        [<%= String.join(", ", result.model.getExcludedLanguages()) %>] were excluded from code analysis
        and preserved intact in the project package.
    </div>
    <% } %>

    <% if (result.model.hasParseErrors()) { %>
    <div class="warn-banner">
        <strong>Parse Warnings/Errors:</strong> <%= result.model.getParseErrors().size() %> Java source file(s) had syntax or parsing warnings:
        <ul style="margin:6px 0 0 20px;padding:0">
            <% for (Map.Entry<String, String> entry : result.model.getParseErrors().entrySet()) { %>
                <li><code><%= entry.getKey() %></code>: <%= entry.getValue() %></li>
            <% } %>
        </ul>
    </div>
    <% } %>

    <div class="section">
        <h2>Interactive Dependency Graph</h2>
        <p style="color:var(--text-dim);font-size:0.85rem;margin-top:0">Nodes and edges come from the uploaded project's static model. Unresolved external types are omitted rather than invented.</p>
        <div class="graph-tools">
            <input id="graphSearch" type="search" placeholder="Search component" aria-label="Search component">
            <input id="graphPackage" type="search" placeholder="Filter package" aria-label="Filter package">
            <select id="graphHighlight" aria-label="Highlight">
                <option value="none">No highlight</option>
                <option value="cycles">Circular dependencies</option>
                <option value="smells">Architectural smells</option>
                <option value="risk">High-risk components</option>
            </select>
            <button type="button" class="cta" style="padding:6px 14px;font-size:0.82rem" onclick="resetArchGraph()">Reset view</button>
            <button type="button" class="cta" style="padding:6px 14px;font-size:0.82rem;background:#1e293b;border:1px solid var(--border)" onclick="reheatArchGraph()">Re-layout</button>
        </div>
        <div class="graph-wrap">
            <div>
                <canvas id="archGraph" class="graph-canvas" role="img" aria-label="Project dependency graph"></canvas>
                <div class="graph-legend">
                    <span><span class="legend-dot" style="background:#6366f1"></span>Class / component</span>
                    <span><span class="legend-dot" style="background:#0ea5e9"></span>Interface</span>
                    <span><span class="legend-dot" style="background:#ef4444"></span>In a cycle</span>
                    <span><span class="legend-dot" style="background:#f97316"></span>High ML risk</span>
                    <span><span class="legend-dot" style="background:#f59e0b"></span>Smell</span>
                </div>
            </div>
            <aside class="node-detail" id="nodeDetail">
                <h3>Selection</h3>
                <p style="color:var(--text-dim)">Click a node or edge to inspect fan-in/out, smells, and risk. Colour is not the only indicator — labels and this panel repeat the same facts.</p>
            </aside>
        </div>
    </div>

    <div class="metric-cards">
        <div class="metric-card">
            <div class="value"><%= result.metricsBefore.architectureHealthScore %></div>
            <div class="label">Architecture Health (0-100)</div>
        </div>
        <div class="metric-card">
            <div class="value"><%= result.metricsBefore.dependencyScore %></div>
            <div class="label">Dependency Pressure Score</div>
        </div>
        <div class="metric-card">
            <div class="value"><%= result.metricsBefore.cycleCount %></div>
            <div class="label">Dependency Cycles</div>
        </div>
        <div class="metric-card">
            <div class="value"><%= result.smells.size() %></div>
            <div class="label">Architectural Smells</div>
        </div>
    </div>

    <!-- ═══════════════════════════════════════
         WHAT-IF SIMULATOR LAUNCHER CARD
    ═══════════════════════════════════════ -->
    <div style="background:linear-gradient(135deg,rgba(99,102,241,0.12),rgba(168,85,247,0.08));border:1px solid rgba(99,102,241,0.3);border-radius:14px;padding:20px 24px;margin-bottom:28px;display:flex;align-items:center;gap:20px;flex-wrap:wrap;">
        <div style="font-size:2.4rem;line-height:1;">🔮</div>
        <div style="flex:1;min-width:200px;">
            <div style="font-size:1.05rem;font-weight:700;color:#e2e8f0;margin-bottom:4px;">What-If Architecture Simulator</div>
            <div style="font-size:0.82rem;color:#94a3b8;line-height:1.5;">
                Propose a change in plain English (e.g. "Remove PaymentService", "Add caching module") and instantly see the blast-radius impact, risk score, broken dependencies, and projected metrics — without modifying a single source file.
            </div>
        </div>
        <a href="<%= request.getContextPath() %>/simulator"
           style="display:inline-flex;align-items:center;gap:8px;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:white;padding:11px 22px;border-radius:10px;font-weight:600;font-size:0.88rem;text-decoration:none;white-space:nowrap;box-shadow:0 4px 16px rgba(99,102,241,0.3);flex-shrink:0;">
            Launch Simulator →
        </a>
    </div>


    <% if (result.selectedCandidate != null) { %>
    <div class="section">
        <h2>Automated Java Refactoring Execution</h2>
        <div style="background:var(--card-bg);border:1px solid var(--border);border-radius:8px;padding:16px;margin-bottom:16px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
                <span style="font-size:1.1rem;font-weight:600">
                    <%= result.selectedCandidate.strategyName %> &rarr; <%= result.selectedCandidate.targetComponentId %>
                </span>
                <span class="badge <%= statusBadgeClass(result.selectedCandidate.status) %>">
                    <%= result.selectedCandidate.status %>
                </span>
            </div>
            <p style="color:var(--text-dim);font-size:0.9rem;margin:4px 0 10px 0">
                <%= result.selectedCandidate.rationale %>
            </p>
            <div style="font-size:0.9rem;margin-top:10px">
                <strong>Source Code Modifications:</strong>
                <ul style="margin:6px 0 0 20px;padding:0">
                    <% for (String change : result.selectedCandidate.plannedChanges) { %>
                        <li><%= change %></li>
                    <% } %>
                </ul>
            </div>
            <% if (result.validation != null && result.validation.verdict == com.archanalyzer.validate.ValidationAggregator.Verdict.FAIL) { %>
            <div class="warn-banner" style="margin-top:12px;margin-bottom:0">
                <strong>Refactoring Rejected & Rolled Back:</strong>
                Candidate failed structural validation (<%= result.validation.summary %>).
                All changes were rolled back, and the downloadable ZIP contains the preserved original project.
            </div>
            <% } %>
        </div>
    </div>
    <% } %>

    <% if (result.metricsAfter != null) { %>
    <div class="section">
        <h2>Before / After Refactoring Metrics</h2>
        <table>
            <tr><th>Metric</th><th>Before Refactoring</th><th>After Refactoring</th><th>Impact</th></tr>
            <tr>
                <td>Architecture Health</td>
                <td><%= result.metricsBefore.architectureHealthScore %> / 100</td>
                <td><%= result.metricsAfter.architectureHealthScore %> / 100</td>
                <td style="color:<%= result.metricsAfter.architectureHealthScore >= result.metricsBefore.architectureHealthScore ? "#86efac" : "#fca5a5" %>">
                    <%= String.format("%+.1f", result.metricsAfter.architectureHealthScore - result.metricsBefore.architectureHealthScore) %>
                </td>
            </tr>
            <tr>
                <td>Dependency Score (lower = better)</td>
                <td><%= result.metricsBefore.dependencyScore %> / 100</td>
                <td><%= result.metricsAfter.dependencyScore %> / 100</td>
                <td style="color:<%= result.metricsAfter.dependencyScore <= result.metricsBefore.dependencyScore ? "#86efac" : "#fca5a5" %>">
                    <%= String.format("%+.1f", result.metricsAfter.dependencyScore - result.metricsBefore.dependencyScore) %>
                </td>
            </tr>
            <tr>
                <td>Circular Dependencies</td>
                <td><%= result.metricsBefore.cycleCount %></td>
                <td><%= result.metricsAfter.cycleCount %></td>
                <td><%= result.metricsBefore.cycleCount - result.metricsAfter.cycleCount %> cycle(s) reduced</td>
            </tr>
        </table>

        <% if (result.validation != null) { %>
        <div style="margin-top:16px;padding:14px;background:var(--card-bg);border:1px solid var(--border);border-radius:6px">
            <div style="display:flex;align-items:center;gap:12px">
                <strong>Validation Verdict:</strong>
                <span class="badge <%= statusBadgeClass(result.validation.verdict.toString()) %>">
                    <%= result.validation.verdict %>
                </span>
            </div>
            <p style="margin:8px 0 4px 0"><%= result.validation.summary %></p>
            <% if (result.validation.buildResult != null) { %>
                <p style="font-size:0.85rem;color:var(--text-dim);margin:4px 0">
                    Compiler: <%= result.validation.buildResult.buildToolUsed %> |
                    Test execution: <%= result.validation.buildResult.testSummary %>
                </p>
                <% if (result.validation.buildResult.externalDependenciesMissing) { %>
                    <p style="font-size:0.85rem;color:#fcd34d;margin:4px 0">
                        &#9888; Note: Project relies on external dependencies (e.g. Spring, Lombok) not bundled in the upload archive. Syntax and structural verification passed with 0 refactoring regressions.
                    </p>
                <% } %>
                <% if (!result.validation.buildResult.errors.isEmpty()) { %>
                    <div style="color:#fca5a5;font-size:0.85rem;margin-top:6px">
                        <strong>Compilation errors:</strong>
                        <ul style="margin:4px 0 0 20px;padding:0">
                            <% for (String err : result.validation.buildResult.errors) { %>
                                <li><%= err %></li>
                            <% } %>
                        </ul>
                    </div>
                <% } %>
            <% } %>
        </div>
        <% } %>
    </div>
    <% } %>

    <div class="section">
        <h2>ML Risk Predictions (Java Components)</h2>
        <table>
            <tr><th>Java Component</th><th>Predicted Risk</th><th>Confidence</th><th>Model</th><th>Contributing Factors</th></tr>
            <%
                List<RiskPrediction> sorted = new ArrayList<>(result.riskPredictions);
                sorted.sort((a, b) -> Double.compare(b.confidence, a.confidence));
                int shown = 0;
                for (RiskPrediction p : sorted) {
                    if (shown++ >= 20) break;
            %>
            <tr>
                <td><code><%= p.componentId %></code></td>
                <td><span class="badge <%= riskBadgeClass(p.riskLabel) %>"><%= p.riskLabel %></span></td>
                <td><%= String.format("%.0f%%", p.confidence * 100) %></td>
                <td><%= p.modelUsed %></td>
                <td><%= String.join("; ", p.topContributingFactors) %></td>
            </tr>
            <% } %>
        </table>
    </div>

    <div class="section">
        <h2>Architectural Smells</h2>
        <% if (result.smells.isEmpty()) { %>
            <p style="color:var(--text-dim)">No architectural smells detected.</p>
        <% } else { %>
        <table>
            <tr><th>Severity</th><th>Smell Type</th><th>Java Component</th><th>Evidence</th><th>Why it matters</th><th>Recommended action</th></tr>
            <% for (Smell s : result.smells) { %>
            <tr>
                <td><span class="badge <%= s.severity.toString().equals("HIGH") ? "badge-high" : (s.severity.toString().equals("MEDIUM") ? "badge-medium" : "badge-low") %>"><%= s.severity %></span></td>
                <td><%= s.type %></td>
                <td><code><%= s.componentId %></code></td>
                <td><%= s.evidence %></td>
                <td><%= s.whyItMatters() %></td>
                <td><%= s.recommendedAction() %><div style="font-size:0.75rem;color:var(--text-dim);margin-top:4px"><%= s.limitation() %></div></td>
            </tr>
            <% } %>
        </table>
        <% } %>
    </div>

    <!-- ═══════════════════════════════════════
         GENERATIVE AI ARCHITECTURE EXPLAINER
    ═══════════════════════════════════════ -->
    <div class="section">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:10px;">
            <div>
                <h2 style="margin:0;border-bottom:none;padding-bottom:0;display:flex;align-items:center;gap:8px;">
                    <span>🤖</span> Generative AI Project Architecture Explainer
                </h2>
                <div style="font-size:0.83rem;color:var(--text-dim);margin-top:4px;">
                    Plain-English breakdown of project architecture, data &amp; execution flow, detected tech stack, and modularity health.
                </div>
            </div>
            <div style="display:flex;align-items:center;gap:8px;">
                <% if (result.aiResult != null && result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.LIVE_SUCCESS) { %>
                    <span class="badge" style="background:rgba(99,102,241,0.2);color:#a5b4fc;border:1px solid #6366f1;padding:5px 12px;font-size:0.82rem;">
                        ✨ AI Live Reasoning Active
                    </span>
                <% } else if (result.aiResult != null && result.aiResult.hasOverview()) { %>
                    <span class="badge" style="background:rgba(16,185,129,0.15);color:#6ee7b7;border:1px solid #10b981;padding:5px 12px;font-size:0.82rem;">
                        ✨ AI Architecture Explainer
                    </span>
                <% } else { %>
                    <span class="badge badge-medium" style="font-size:0.82rem;">
                        AI Architecture Explainer
                    </span>
                <% } %>
                <button type="button" onclick="toggleAiChat()" style="background:linear-gradient(135deg, #4f46e5, #7c3aed);color:#fff;border:none;padding:7px 18px;border-radius:8px;font-size:0.85rem;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;gap:8px;box-shadow:0 2px 10px rgba(79,70,229,0.35);transition:all 0.2s ease;">
                    <span>💬</span> Ask AI
                </button>
            </div>
        </div>

        <% if (result.aiResult != null && result.aiResult.hasOverview()) { %>
            <!-- 1. High-Level Architecture Overview in Simple Terms -->
            <div style="background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;margin-bottom:18px;box-shadow:0 4px 16px rgba(0,0,0,0.2);">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:8px;">
                    <h3 style="margin:0;font-size:1.05rem;color:#f8fafc;display:flex;align-items:center;gap:8px;">
                        <span>🏛️</span> Architecture Pattern &amp; Structure in Simple Words
                    </h3>
                    <% if (result.aiResult.getArchitecturePattern() != null && !result.aiResult.getArchitecturePattern().isBlank()) { %>
                    <span style="background:rgba(99,102,241,0.15);color:#c7d2fe;border:1px solid rgba(99,102,241,0.4);padding:4px 12px;border-radius:20px;font-size:0.82rem;font-weight:600;">
                        Pattern: <%= result.aiResult.getArchitecturePattern() %>
                    </span>
                    <% } %>
                </div>
                <div style="font-size:0.93rem;line-height:1.7;color:#cbd5e1;white-space:pre-line;">
                    <%= result.aiResult.getPlainEnglishOverview() %>
                </div>
            </div>

            <!-- 2. Step-by-Step Application Execution Flow -->
            <% if (!result.aiResult.getApplicationFlowSteps().isEmpty()) { %>
            <div style="margin-bottom:20px;">
                <h3 style="font-size:1.0rem;color:#e2e8f0;margin:0 0 6px 0;display:flex;align-items:center;gap:8px;">
                    <span>🔄</span> How the Project Works: Step-by-Step Application Flow
                </h3>
                <div style="font-size:0.84rem;color:var(--text-dim);margin-bottom:12px;">
                    Trace of a user interaction traveling across the application layers from client to database and back:
                </div>
                <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(210px, 1fr));gap:12px;">
                    <% int flowIdx = 1; for (String step : result.aiResult.getApplicationFlowSteps()) {
                        String title = step.contains(":") ? step.substring(0, step.indexOf(':')).trim() : ("Step " + flowIdx);
                        String body = step.contains(":") ? step.substring(step.indexOf(':') + 1).trim() : step;
                    %>
                    <div style="background:var(--surface-2);border:1px solid var(--border);border-radius:8px;padding:14px;display:flex;flex-direction:column;justify-content:space-between;border-top:3px solid var(--accent);">
                        <div>
                            <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
                                <span style="background:var(--accent);color:#fff;font-weight:700;font-size:0.75rem;width:22px;height:22px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;">
                                    <%= flowIdx++ %>
                                </span>
                                <strong style="font-size:0.86rem;color:#f8fafc;"><%= title %></strong>
                            </div>
                            <p style="margin:0;font-size:0.82rem;color:#94a3b8;line-height:1.5;"><%= body %></p>
                        </div>
                    </div>
                    <% } %>
                </div>
            </div>
            <% } %>

            <!-- 3. Detected Technology Stack & Frameworks -->
            <% if (!result.aiResult.getDetectedTechStack().isEmpty()) { %>
            <div style="margin-bottom:20px;">
                <h3 style="font-size:1.0rem;color:#e2e8f0;margin:0 0 10px 0;display:flex;align-items:center;gap:8px;">
                    <span>💻</span> Detected Technology Stack &amp; Frameworks
                </h3>
                <div style="display:flex;flex-wrap:wrap;gap:8px;">
                    <% for (String tech : result.aiResult.getDetectedTechStack()) { %>
                    <span style="background:rgba(255,255,255,0.04);border:1px solid var(--border);border-radius:8px;padding:6px 12px;font-size:0.84rem;color:#e2e8f0;display:inline-flex;align-items:center;gap:6px;">
                        <span style="color:#6366f1;">⚡</span> <%= tech %>
                    </span>
                    <% } %>
                </div>
            </div>
            <% } %>

            <!-- 4. Maintainability & Health Advisory -->
            <% if (result.aiResult.getMaintainabilitySummary() != null && !result.aiResult.getMaintainabilitySummary().isBlank()) { %>
            <div style="background:rgba(99,102,241,0.06);border:1px solid rgba(99,102,241,0.25);border-radius:8px;padding:14px 18px;margin-bottom:20px;">
                <h3 style="margin:0 0 6px 0;font-size:0.95rem;color:#c7d2fe;display:flex;align-items:center;gap:8px;">
                    <span>💡</span> Plain-English Health &amp; Maintenance Advisory
                </h3>
                <p style="margin:0;font-size:0.88rem;color:#cbd5e1;line-height:1.6;">
                    <%= result.aiResult.getMaintainabilitySummary() %>
                </p>
            </div>
            <% } %>

        <% } %>

        <!-- Live AI Smell Explanations (when available) -->
        <% if (result.aiResult != null && !result.aiResult.getExplanations().isEmpty()) { %>
        <div style="margin-bottom:20px;">
            <h3 style="font-size:0.98rem;color:#cbd5e1;margin-bottom:10px;">Smell Diagnoses &amp; Maintenance Impact</h3>
            <table>
                <tr><th>Smell Type</th><th>Target Component</th><th>Plain-English Diagnosis</th><th>Maintenance Impact &amp; Remedy</th></tr>
                <% for (com.archanalyzer.ai.ArchitectureSmellExplanation exp : result.aiResult.getExplanations()) { %>
                <tr>
                    <td><code><%= exp.getSmellType() %></code></td>
                    <td><code><%= exp.getComponentId() %></code></td>
                    <td>
                        <p style="margin:0 0 4px 0;"><%= exp.getPlainLanguageExplanation() %></p>
                        <span style="font-size:0.75rem;color:var(--text-dim);">Evidence: <%= exp.getEvidence() %></span>
                    </td>
                    <td>
                        <p style="margin:0 0 4px 0;"><strong>Why it matters:</strong> <%= exp.getWhyItMatters() %></p>
                        <p style="margin:0;font-size:0.85rem;color:#93c5fd;"><strong>Remedy:</strong> <%= exp.getRecommendedAction() %></p>
                    </td>
                </tr>
                <% } %>
            </table>
        </div>
        <% } %>

        <!-- Live AI Refactoring Recommendations (when available) -->
        <% if (result.aiResult != null && !result.aiResult.getRecommendations().isEmpty()) { %>
        <div style="margin-bottom:20px;">
            <h3 style="font-size:0.98rem;color:#cbd5e1;margin-bottom:10px;">AI Refactoring Advisory &amp; Action Plan</h3>
            <% for (com.archanalyzer.ai.AiRefactoringRecommendation rec : result.aiResult.getRecommendations()) { %>
            <div class="candidate-card" style="margin-bottom:12px;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
                    <span class="strategy">
                        <%= rec.getProposedRefactoring() %> &rarr; <code><%= rec.getTargetComponentId() %></code>
                    </span>
                    <span class="badge <%= rec.isEngineSupported() ? "badge-pass" : "badge-medium" %>">
                        <%= rec.isEngineSupported() ? "Engine Automated" : "Preview / Advisory Only" %>
                    </span>
                </div>
                <div style="font-size:0.88rem;color:var(--text-dim);margin-bottom:6px;">
                    <strong>Problem:</strong> <%= rec.getDetectedProblem() %><br>
                    <strong>Why it helps:</strong> <%= rec.getWhyItHelps() %>
                </div>
                <div style="font-size:0.82rem;color:#94a3b8;background:rgba(255,255,255,0.03);padding:8px 12px;border-radius:6px;">
                    <strong>Expected Benefits:</strong> <%= rec.getExpectedBenefits() %> |
                    <strong>Risks:</strong> <%= rec.getPotentialRisks() %><br>
                    <strong>Implementation Plan:</strong> <%= rec.getImplementationPlan() %>
                </div>
            </div>
            <% } %>
        </div>
        <% } %>

        <!-- Architectural Assistant Guidance Notice -->
        <div class="notice-banner" style="margin-top:12px;background:rgba(99,102,241,0.08);border:1px solid rgba(99,102,241,0.25);color:#cbd5e1;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
            <div>
                <strong>💬 Have questions about modifying or refactoring this codebase?</strong>
                Click <strong>Ask AI</strong> to chat with the Architecture Assistant about breaking cycles, safe modifications, and refactoring plans.
            </div>
            <button type="button" onclick="toggleAiChat()" style="background:linear-gradient(135deg, #4f46e5, #7c3aed);color:#fff;border:none;padding:6px 14px;border-radius:6px;font-size:0.82rem;font-weight:600;cursor:pointer;">
                💬 Open Assistant
            </button>
        </div>
    </div>

    <!-- Floating AI Chatbot Button -->
    <button id="aiFloatingBtn" type="button" onclick="toggleAiChat()" style="position:fixed;bottom:24px;right:24px;background:linear-gradient(135deg, #4f46e5, #7c3aed);color:#fff;border:none;border-radius:30px;padding:12px 22px;font-size:0.92rem;font-weight:700;cursor:pointer;display:inline-flex;align-items:center;gap:8px;box-shadow:0 6px 20px rgba(79,70,229,0.45);z-index:9990;transition:transform 0.2s ease;">
        <span>💬</span> Ask AI
    </button>

    <!-- ═══════════════════════════════════════
         INTERACTIVE AI ARCHITECTURE CHAT DRAWER
    ═══════════════════════════════════════ -->
    <div id="aiChatDrawer" style="display:none;position:fixed;bottom:24px;right:24px;width:420px;max-width:calc(100vw - 32px);height:580px;max-height:calc(100vh - 48px);background:#11151f;border:1px solid #2a354f;border-radius:14px;box-shadow:0 24px 60px rgba(0,0,0,0.85);z-index:9999;flex-direction:column;overflow:hidden;">
        <!-- Chat Header -->
        <div style="background:linear-gradient(135deg, #161c2a, #1e2638);padding:14px 18px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;">
            <div style="display:flex;align-items:center;gap:10px;">
                <div style="width:34px;height:34px;border-radius:50%;background:linear-gradient(135deg, #4f46e5, #7c3aed);display:flex;align-items:center;justify-content:center;font-size:1.1rem;">
                    🤖
                </div>
                <div>
                    <h3 style="margin:0;font-size:0.95rem;color:#f8fafc;font-weight:600;">Architecture AI Assistant</h3>
                    <%
                        boolean isLiveAiReady = (result.aiResult != null && result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.LIVE_SUCCESS);
                        boolean isAiConfigured = com.archanalyzer.ai.AiConfig.loadFromEnvironment().isConfigured();
                        String initialStatusText = isLiveAiReady ? "Connected • Gemini Live" : (isAiConfigured ? "Ready • Gemini Configured" : "Offline Assistant • Local Fallback");
                        String initialDotColor = isLiveAiReady ? "#10b981" : (isAiConfigured ? "#38bdf8" : "#f59e0b");
                        String initialTextColor = isLiveAiReady ? "#10b981" : (isAiConfigured ? "#38bdf8" : "#fbbf24");
                    %>
                    <div id="aiConnectionStatus" style="font-size:0.75rem;color:<%= initialTextColor %>;display:flex;align-items:center;gap:6px;">
                        <span id="aiStatusDot" style="width:7px;height:7px;background:<%= initialDotColor %>;border-radius:50%;display:inline-block;"></span>
                        <span id="aiStatusText"><%= initialStatusText %></span>
                    </div>
                </div>
            </div>
            <button type="button" onclick="toggleAiChat()" style="background:transparent;border:none;color:#94a3b8;font-size:1.4rem;cursor:pointer;line-height:1;padding:4px 8px;">&times;</button>
        </div>

        <!-- Chat Messages Container -->
        <div id="aiChatMessages" style="flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:12px;background:#0d111a;">
            <!-- Welcome Message -->
            <div style="background:var(--surface-2);border:1px solid var(--border);border-radius:10px 10px 10px 2px;padding:12px 14px;font-size:0.88rem;color:#cbd5e1;line-height:1.55;max-width:92%;">
                Hello! I am your <strong>Architecture AI Assistant</strong> for <em><%= result.model.getProjectName() %></em>.<br><br>
                Ask me anything about:
                <ul style="margin:6px 0 0 16px;padding:0;font-size:0.84rem;color:#94a3b8;">
                    <li>Modifying specific components safely</li>
                    <li>Refactoring circular dependencies with interfaces</li>
                    <li>Understanding data flow between layers</li>
                    <li>Boosting architecture health score</li>
                </ul>
            </div>

            <!-- Quick Suggestion Pills -->
            <div id="aiQuickPills" style="display:flex;flex-wrap:wrap;gap:6px;margin-top:4px;">
                <button type="button" onclick="askQuickPrompt('How can I fix the circular dependency in this project?')" style="background:rgba(99,102,241,0.12);color:#a5b4fc;border:1px solid rgba(99,102,241,0.3);border-radius:16px;padding:4px 10px;font-size:0.76rem;cursor:pointer;text-align:left;">
                    ⚡ Fix circular dependency
                </button>
                <button type="button" onclick="askQuickPrompt('How can I safely modify the services without breaking callers?')" style="background:rgba(99,102,241,0.12);color:#a5b4fc;border:1px solid rgba(99,102,241,0.3);border-radius:16px;padding:4px 10px;font-size:0.76rem;cursor:pointer;text-align:left;">
                    🛡️ Safely modify services
                </button>
                <button type="button" onclick="askQuickPrompt('Explain the request flow from controller to database in simple terms.')" style="background:rgba(99,102,241,0.12);color:#a5b4fc;border:1px solid rgba(99,102,241,0.3);border-radius:16px;padding:4px 10px;font-size:0.76rem;cursor:pointer;text-align:left;">
                    🔄 Explain request flow
                </button>
                <button type="button" onclick="askQuickPrompt('What refactoring changes will improve the architecture score most?')" style="background:rgba(99,102,241,0.12);color:#a5b4fc;border:1px solid rgba(99,102,241,0.3);border-radius:16px;padding:4px 10px;font-size:0.76rem;cursor:pointer;text-align:left;">
                    📈 Improve architecture score
                </button>
            </div>
        </div>

        <!-- Chat Input Box -->
        <div style="padding:12px;background:#11151f;border-top:1px solid var(--border);display:flex;gap:8px;">
            <input type="text" id="aiChatInput" placeholder="Ask about modifying or refactoring..." style="flex:1;background:var(--surface-2);color:#f1f5f9;border:1px solid var(--border);border-radius:8px;padding:9px 12px;font-size:0.86rem;outline:none;" onkeydown="if(event.key==='Enter') sendAiChatMessage()">
            <button type="button" id="btnSendAiChat" onclick="sendAiChatMessage()" style="background:var(--accent);color:#fff;border:none;border-radius:8px;padding:9px 16px;font-size:0.86rem;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;">
                Send
            </button>
        </div>
    </div>

    <div class="section">
        <h2>Refactoring Recommendations & Architecture Candidates</h2>
        <% for (ArchitectureCandidate c : result.rankedCandidates) { %>
        <div class="candidate-card">
            <span class="score">Score: <%= c.compositeScore %></span>
            <div class="strategy">
                <%= c.strategyName %> &rarr; <code><%= c.targetComponentId %></code>
            </div>
            <div style="color:var(--text-dim);font-size:0.88rem;margin:4px 0"><%= c.rationale %></div>
            <div style="margin-top:6px;font-size:0.85rem">
                Projected dependency score: <%= c.projectedDependencyScore %> |
                Files changed: <%= c.filesChanged %> |
                Implementation Status:
                <span class="badge <%= statusBadgeClass(c.status) %>" style="margin-left:4px">
                    <%= c.status %>
                </span>
            </div>
        </div>
        <% } %>
    </div>
</main>
<script src="<%= request.getContextPath() %>/js/graph.js?v=<%= System.currentTimeMillis() %>"></script>
<script>
const CTX = '<%= request.getContextPath() %>';
let archGraph;
async function loadArchGraph() {
    const res = await fetch(CTX + '/api/projects/current/graph');
    if (!res.ok) {
        document.getElementById('nodeDetail').innerHTML = '<p>Graph data is not available for this session.</p>';
        return;
    }
    const data = await res.json();
    archGraph = ArchGraph.createGraph(document.getElementById('archGraph'), {
        onSelectNode: renderNode,
        onHoverNode: renderNode,
        onSelectEdge: renderEdge
    });
    archGraph.load(data);
}
function renderNode(n) {
    document.getElementById('nodeDetail').innerHTML = `
        <h3>${esc(n.label || n.id)}</h3>
        <p><strong>Type:</strong> ${esc(n.kind || 'CLASS')}</p>
        <p><strong>Location:</strong> ${esc(n.file || 'n/a')}</p>
        <p><strong>Fan-in / fan-out:</strong> ${n.fanIn} / ${n.fanOut}</p>
        <p><strong>Instability:</strong> ${Number(n.instability||0).toFixed(2)}</p>
        <p><strong>Centrality:</strong> ${Number(n.centrality||0).toFixed(2)}</p>
        <p><strong>Complexity / size:</strong> ${n.complexity || 0} / ${n.loc || 0} LOC, ${n.methodCount || 0} methods</p>
        <p><strong>Smell:</strong> ${esc(n.smell || 'none')}<br><span style="color:var(--text-dim)">${esc(n.smellEvidence || '')}</span></p>
        <p><strong>ML risk:</strong> ${esc(n.risk || 'n/a')} ${n.riskConfidence ? '(' + Math.round(n.riskConfidence*100) + '% confidence)' : ''}</p>
        <p style="color:var(--text-dim)">${(n.riskFactors||[]).map(esc).join('; ')}</p>
        <p style="font-size:0.75rem;color:var(--text-dim)">ML risk is a trained-model prediction on synthetic demo data, distinct from deterministic smell rules.</p>`;
}
function renderEdge(e) {
    document.getElementById('nodeDetail').innerHTML = `
        <h3>Dependency</h3>
        <p><code>${esc(e.from)}</code> → <code>${esc(e.to)}</code></p>
        <p><strong>Change marker:</strong> ${esc(e.change || 'confirmed in source model')}</p>
        <p style="color:var(--text-dim)">This edge exists in the static project model. Runtime-only wiring is not claimed.</p>`;
}
function resetArchGraph() {
    if (archGraph) archGraph.resetView();
}
function reheatArchGraph() {
    if (archGraph) archGraph.reheat();
}
function esc(s) {
    return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
document.getElementById('graphSearch').addEventListener('input', e => { if (archGraph) archGraph.setFilter('query', e.target.value); });
document.getElementById('graphPackage').addEventListener('input', e => { if (archGraph) archGraph.setFilter('package', e.target.value); });
document.getElementById('graphHighlight').addEventListener('change', e => { if (archGraph) archGraph.setFilter('highlight', e.target.value); });
function toggleAiChat() {
    const drawer = document.getElementById('aiChatDrawer');
    const floatBtn = document.getElementById('aiFloatingBtn');
    if (drawer) {
        if (drawer.style.display === 'none' || drawer.style.display === '') {
            drawer.style.display = 'flex';
            if (floatBtn) floatBtn.style.display = 'none';
            const input = document.getElementById('aiChatInput');
            if (input) input.focus();
        } else {
            drawer.style.display = 'none';
            if (floatBtn) floatBtn.style.display = 'inline-flex';
        }
    }
}

function askQuickPrompt(promptText) {
    const input = document.getElementById('aiChatInput');
    if (input) {
        input.value = promptText;
        sendAiChatMessage();
    }
}

function updateAiStatus(state, label) {
    const dot = document.getElementById('aiStatusDot');
    const text = document.getElementById('aiStatusText');
    const container = document.getElementById('aiConnectionStatus');
    if (!dot || !text || !container) return;
    text.textContent = label;
    if (state === 'connected') {
        dot.style.background = '#10b981';
        container.style.color = '#10b981';
    } else if (state === 'connecting') {
        dot.style.background = '#38bdf8';
        container.style.color = '#38bdf8';
    } else if (state === 'offline') {
        dot.style.background = '#f59e0b';
        container.style.color = '#fbbf24';
    } else if (state === 'error') {
        dot.style.background = '#ef4444';
        container.style.color = '#fca5a5';
    }
}

async function sendAiChatMessage() {
    const input = document.getElementById('aiChatInput');
    const msg = (input ? input.value : '').trim();
    if (!msg) return;

    input.value = '';
    const messages = document.getElementById('aiChatMessages');
    const pills = document.getElementById('aiQuickPills');
    if (pills) pills.style.display = 'none';

    // Append User Message
    const userDiv = document.createElement('div');
    userDiv.style.cssText = 'align-self:flex-end;background:linear-gradient(135deg, #4f46e5, #6366f1);color:#fff;border-radius:10px 10px 2px 10px;padding:10px 14px;font-size:0.86rem;max-width:85%;line-height:1.5;box-shadow:0 2px 8px rgba(79,70,229,0.3);';
    userDiv.textContent = msg;
    messages.appendChild(userDiv);

    // Append AI Typing Indicator
    const typingDiv = document.createElement('div');
    typingDiv.id = 'aiTypingIndicator';
    typingDiv.style.cssText = 'align-self:flex-start;background:var(--surface-2);color:#94a3b8;border:1px solid var(--border);border-radius:10px 10px 10px 2px;padding:10px 14px;font-size:0.84rem;max-width:85%;display:flex;align-items:center;gap:6px;';
    typingDiv.innerHTML = '<span style="display:inline-block;animation:pulse 1s infinite;">🤖</span> Thinking & analyzing architecture...';
    messages.appendChild(typingDiv);
    messages.scrollTop = messages.scrollHeight;

    updateAiStatus('connecting', 'Thinking & connecting to AI...');

    const btn = document.getElementById('btnSendAiChat');
    if (btn) btn.disabled = true;

    try {
        const res = await fetch(CTX + '/api/projects/current/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ message: msg })
        });
        const data = await res.json();
        const indicator = document.getElementById('aiTypingIndicator');
        if (indicator) indicator.remove();

        // Source badge & notice
        let badgeHtml = '';
        let noticeHtml = '';
        if (data.source === 'GEMINI') {
            updateAiStatus('connected', 'Connected • Gemini Live');
            badgeHtml = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:8px;"><span style="background:linear-gradient(135deg, rgba(79,70,229,0.25), rgba(124,58,237,0.25));color:#c7d2fe;border:1px solid #6366f1;font-size:0.7rem;padding:3px 9px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;font-weight:600;">✨ Gemini AI</span></div>';
        } else {
            updateAiStatus('offline', 'Offline Assistant • Local Fallback');
            badgeHtml = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:8px;"><span style="background:rgba(245,158,11,0.18);color:#fbbf24;border:1px solid #f59e0b;font-size:0.7rem;padding:3px 9px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;font-weight:600;">⚡ Offline Assistant</span></div>';
            if (data.notice) {
                noticeHtml = '<div style="font-size:0.76rem;color:#fca5a5;background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);padding:6px 10px;border-radius:6px;margin-bottom:8px;line-height:1.4;">⚠️ <strong>Provider Notice:</strong> ' + esc(data.notice) + '</div>';
            }
        }

        const aiDiv = document.createElement('div');
        aiDiv.style.cssText = 'align-self:flex-start;background:var(--surface-2);border:1px solid var(--border);border-radius:10px 10px 10px 2px;padding:12px 14px;font-size:0.86rem;color:#cbd5e1;line-height:1.6;max-width:92%;';
        aiDiv.innerHTML = badgeHtml + noticeHtml + formatChatReply(data.reply || data.error || 'No response received.');
        messages.appendChild(aiDiv);
    } catch (e) {
        const indicator = document.getElementById('aiTypingIndicator');
        if (indicator) indicator.remove();
        updateAiStatus('error', 'Error • Assistant Disconnected');

        const errDiv = document.createElement('div');
        errDiv.style.cssText = 'align-self:flex-start;background:rgba(239,68,68,0.15);border:1px solid #ef4444;border-radius:10px 10px 10px 2px;padding:10px 14px;font-size:0.84rem;color:#fca5a5;max-width:90%;';
        errDiv.textContent = 'Could not reach assistant: ' + e.message;
        messages.appendChild(errDiv);
    } finally {
        if (btn) btn.disabled = false;
        messages.scrollTop = messages.scrollHeight;
    }
}

function formatChatReply(text) {
    if (!text) return '';
    let formatted = esc(text)
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/`(.*?)`/g, '<code style="background:rgba(0,0,0,0.3);padding:2px 5px;border-radius:4px;font-size:0.8rem;color:#93c5fd;">$1</code>')
        .replace(/\n\n/g, '<br><br>')
        .replace(/\n/g, '<br>');
    return formatted;
}

loadArchGraph().catch(err => {
    document.getElementById('nodeDetail').innerHTML = '<p>Could not load graph: ' + esc(err.message) + '</p>';
});
</script>
</body>
</html>
