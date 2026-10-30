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
         GENERATIVE AI ARCHITECTURE REASONING
    ═══════════════════════════════════════ -->
    <div class="section">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:10px;">
            <h2 style="margin:0;">Generative AI Architecture Explanations &amp; Advisory</h2>
            <% if (result.aiResult != null && result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.LIVE_SUCCESS) { %>
                <span class="badge" style="background:rgba(99,102,241,0.2);color:#a5b4fc;border:1px solid #6366f1;padding:4px 10px;font-size:0.82rem;">
                    ✨ Live AI (<%= result.aiResult.getProvider() %> / <%= result.aiResult.getModel() %>)
                </span>
            <% } else if (result.aiResult != null && result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.FAILED) { %>
                <span class="badge badge-high" style="font-size:0.82rem;">
                    AI Service Failed
                </span>
            <% } else { %>
                <span class="badge badge-medium" style="font-size:0.82rem;">
                    AI Service Unconfigured / Standby
                </span>
            <% } %>
        </div>

        <% if (result.aiResult == null || result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.UNCONFIGURED || result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.DISABLED) { %>
            <div class="notice-banner" style="margin-bottom:16px;">
                <strong>Generative AI Advisory:</strong>
                <%= (result.aiResult != null && !result.aiResult.getMessage().isBlank()) ? result.aiResult.getMessage() : "Generative AI service is currently unconfigured. Set LLM_API_KEY and LLM_PROVIDER in .env to enable live AI reasoning." %>
                <div style="font-size:0.82rem;color:var(--text-dim);margin-top:6px;">
                    Deterministic static analysis, Tarjan SCC graph cycles, rule-based smell detection, and the trained Random Forest ML model operate independently and are fully displayed above and below.
                </div>
            </div>
        <% } else if (result.aiResult.getStatus() == com.archanalyzer.ai.AiAnalysisResult.Status.FAILED) { %>
            <div class="warn-banner" style="margin-bottom:16px;">
                <strong>AI Service Error (<%= result.aiResult.getProvider() %>):</strong>
                <%= result.aiResult.getMessage() %>
                <div style="font-size:0.82rem;color:var(--text-dim);margin-top:6px;">
                    Please check API key validity, network connectivity, or endpoint configuration. Local deterministic analysis results remain valid.
                </div>
            </div>
        <% } else { %>
            <!-- Live AI Explanations -->
            <% if (!result.aiResult.getExplanations().isEmpty()) { %>
            <div style="margin-bottom:20px;">
                <h3 style="font-size:0.95rem;color:#cbd5e1;margin-bottom:10px;">Smell Diagnoses &amp; Maintenance Impact</h3>
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

            <!-- Live AI Refactoring Recommendations -->
            <% if (!result.aiResult.getRecommendations().isEmpty()) { %>
            <div style="margin-bottom:20px;">
                <h3 style="font-size:0.95rem;color:#cbd5e1;margin-bottom:10px;">AI Refactoring Advisory &amp; Action Plan</h3>
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
        <% } %>
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
<script src="<%= request.getContextPath() %>/js/graph.js"></script>
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
function esc(s) {
    return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
document.getElementById('graphSearch').addEventListener('input', e => { if (archGraph) archGraph.setFilter('query', e.target.value); });
document.getElementById('graphPackage').addEventListener('input', e => { if (archGraph) archGraph.setFilter('package', e.target.value); });
document.getElementById('graphHighlight').addEventListener('change', e => { if (archGraph) archGraph.setFilter('highlight', e.target.value); });
loadArchGraph().catch(err => {
    document.getElementById('nodeDetail').innerHTML = '<p>Could not load graph: ' + esc(err.message) + '</p>';
});
</script>
</body>
</html>
