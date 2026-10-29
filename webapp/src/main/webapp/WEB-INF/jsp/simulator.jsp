<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.archanalyzer.pipeline.AnalyzerPipeline" %>
<%@ page import="com.archanalyzer.model.ProjectModel" %>
<%@ page import="java.util.*" %>
<%
    AnalyzerPipeline.PipelineResult result = (AnalyzerPipeline.PipelineResult) request.getAttribute("result");
    if (result == null) {
        response.sendRedirect(request.getContextPath() + "/projects/upload");
        return;
    }
    ProjectModel model = result.model;
    // Build component list for the selector
    List<ProjectModel.ComponentNode> components = new ArrayList<>(model.getComponents());
    components.sort((a, b) -> a.getSimpleName().compareToIgnoreCase(b.getSimpleName()));
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>What-If Simulator — <%= model.getProjectName() %></title>
    <meta name="description" content="AI-powered What-If Architecture Change Simulator for <%= model.getProjectName() %>. Predict risk, impact, and breaking changes before touching any code.">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/main.css">
    <style>
        /* ══════════════════════════════════
           SIMULATOR-SPECIFIC OVERRIDES
        ══════════════════════════════════ */
        :root {
            --sim-bg: #0a0d14;
            --sim-panel: #11151f;
            --sim-panel-2: #161c2a;
            --sim-border: #1e2840;
            --sim-accent: #6366f1;
            --sim-accent-glow: rgba(99,102,241,0.35);
            --sim-success: #10b981;
            --sim-warning: #f59e0b;
            --sim-danger: #ef4444;
            --sim-danger-glow: rgba(239,68,68,0.25);
            --sim-teal: #0ea5e9;
            --sim-purple: #a855f7;
        }

        body {
            background: var(--sim-bg);
            font-family: 'Inter', system-ui, sans-serif;
        }

        /* Topbar */
        .topbar {
            background: rgba(17,21,31,0.95);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--sim-border);
            padding: 14px 32px;
            position: sticky;
            top: 0;
            z-index: 100;
        }
        .topbar .brand {
            background: linear-gradient(135deg, #6366f1, #a855f7);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            font-size: 1.1rem;
            font-weight: 700;
        }

        /* Page Layout */
        .sim-layout {
            display: grid;
            grid-template-columns: 420px 1fr;
            gap: 0;
            min-height: calc(100vh - 60px);
        }

        /* ─── LEFT PANEL: Input Form ─── */
        .sim-input-panel {
            background: var(--sim-panel);
            border-right: 1px solid var(--sim-border);
            padding: 28px 24px;
            display: flex;
            flex-direction: column;
            gap: 20px;
            overflow-y: auto;
        }

        .sim-panel-header {
            padding-bottom: 16px;
            border-bottom: 1px solid var(--sim-border);
        }
        .sim-panel-header h1 {
            font-size: 1.3rem;
            font-weight: 700;
            margin: 0 0 4px 0;
            background: linear-gradient(135deg, #e2e8f0, #a5b4fc);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .sim-panel-header .subtitle {
            font-size: 0.82rem;
            color: #64748b;
            margin: 0;
        }

        /* Project Badge */
        .project-badge {
            display: flex;
            align-items: center;
            gap: 10px;
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            padding: 12px 14px;
        }
        .project-badge .icon {
            width: 36px;
            height: 36px;
            border-radius: 8px;
            background: linear-gradient(135deg, var(--sim-accent), var(--sim-purple));
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.1rem;
            flex-shrink: 0;
        }
        .project-badge .info { flex: 1; min-width: 0; }
        .project-badge .name {
            font-weight: 600;
            font-size: 0.92rem;
            color: #e2e8f0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .project-badge .meta { font-size: 0.75rem; color: #64748b; }

        /* Form elements */
        .form-group { display: flex; flex-direction: column; gap: 6px; }
        .form-label {
            font-size: 0.78rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            color: #94a3b8;
        }

        .sim-textarea {
            width: 100%;
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            color: #e2e8f0;
            font-family: 'Inter', sans-serif;
            font-size: 0.9rem;
            padding: 14px;
            resize: vertical;
            min-height: 110px;
            transition: border-color 0.2s, box-shadow 0.2s;
            outline: none;
        }
        .sim-textarea:focus {
            border-color: var(--sim-accent);
            box-shadow: 0 0 0 3px var(--sim-accent-glow);
        }
        .sim-textarea::placeholder { color: #475569; }

        .sim-select, .sim-input {
            width: 100%;
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            color: #e2e8f0;
            font-family: 'Inter', sans-serif;
            font-size: 0.88rem;
            padding: 10px 14px;
            transition: border-color 0.2s, box-shadow 0.2s;
            outline: none;
            appearance: none;
        }
        .sim-select:focus, .sim-input:focus {
            border-color: var(--sim-accent);
            box-shadow: 0 0 0 3px var(--sim-accent-glow);
        }
        .sim-select option { background: #161c2a; }

        /* Quick prompts */
        .quick-prompts { display: flex; flex-direction: column; gap: 6px; }
        .quick-prompt-btn {
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 8px;
            color: #94a3b8;
            font-size: 0.78rem;
            padding: 8px 12px;
            text-align: left;
            cursor: pointer;
            transition: all 0.15s;
            line-height: 1.4;
        }
        .quick-prompt-btn:hover {
            border-color: var(--sim-accent);
            color: #c7d2fe;
            background: rgba(99,102,241,0.08);
        }

        /* Run button */
        .btn-simulate {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: white;
            border: none;
            border-radius: 10px;
            padding: 13px 24px;
            font-size: 0.92rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            width: 100%;
            letter-spacing: 0.3px;
            box-shadow: 0 4px 20px rgba(99,102,241,0.3);
        }
        .btn-simulate:hover:not(:disabled) {
            transform: translateY(-1px);
            box-shadow: 0 6px 28px rgba(99,102,241,0.45);
        }
        .btn-simulate:disabled {
            opacity: 0.5;
            cursor: not-allowed;
            transform: none;
        }

        .btn-approve {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: linear-gradient(135deg, #10b981, #059669);
            color: white;
            border: none;
            border-radius: 10px;
            padding: 11px 20px;
            font-size: 0.88rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            flex: 1;
            box-shadow: 0 4px 16px rgba(16,185,129,0.25);
        }
        .btn-approve:hover:not(:disabled) {
            transform: translateY(-1px);
            box-shadow: 0 6px 22px rgba(16,185,129,0.4);
        }
        .btn-approve:disabled { opacity: 0.4; cursor: not-allowed; }

        .btn-secondary {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            background: var(--sim-panel-2);
            color: #94a3b8;
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            padding: 11px 16px;
            font-size: 0.85rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.15s;
        }
        .btn-secondary:hover { color: #e2e8f0; border-color: #475569; }

        /* ─── RIGHT PANEL: Results ─── */
        .sim-results-panel {
            background: var(--sim-bg);
            padding: 28px 32px;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        /* State: Empty / Loading / Error */
        .state-empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            text-align: center;
            padding: 60px;
            color: #475569;
        }
        .state-empty .big-icon {
            font-size: 4rem;
            margin-bottom: 20px;
            opacity: 0.5;
            animation: float 3s ease-in-out infinite;
        }
        @keyframes float {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-8px); }
        }
        .state-empty h2 { color: #64748b; font-size: 1.3rem; margin-bottom: 8px; }
        .state-empty p { font-size: 0.88rem; max-width: 400px; line-height: 1.6; }

        .sim-loading {
            display: none;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            text-align: center;
            padding: 60px;
        }
        .sim-loading.visible { display: flex; }
        .sim-spinner {
            width: 56px;
            height: 56px;
            border: 3px solid var(--sim-border);
            border-top-color: var(--sim-accent);
            border-radius: 50%;
            animation: spin 0.9s linear infinite;
            margin-bottom: 20px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .sim-loading .loading-text { color: #94a3b8; font-size: 0.9rem; }
        .sim-loading .loading-steps {
            margin-top: 16px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            font-size: 0.8rem;
            color: #64748b;
        }
        .sim-loading .loading-steps span { opacity: 0; animation: fadeInStep 0.5s forwards; }
        .sim-loading .loading-steps span:nth-child(1) { animation-delay: 0.3s; }
        .sim-loading .loading-steps span:nth-child(2) { animation-delay: 0.9s; }
        .sim-loading .loading-steps span:nth-child(3) { animation-delay: 1.5s; }
        .sim-loading .loading-steps span:nth-child(4) { animation-delay: 2.1s; }
        @keyframes fadeInStep { to { opacity: 1; } }

        /* Result sections */
        .result-grid {
            display: none;
            flex-direction: column;
            gap: 20px;
        }
        .result-grid.visible { display: flex; }

        .result-section {
            background: var(--sim-panel);
            border: 1px solid var(--sim-border);
            border-radius: 14px;
            overflow: hidden;
        }
        .result-section-header {
            padding: 14px 18px;
            border-bottom: 1px solid var(--sim-border);
            display: flex;
            align-items: center;
            gap: 10px;
            background: var(--sim-panel-2);
        }
        .result-section-header .icon { font-size: 1.1rem; }
        .result-section-header h3 {
            font-size: 0.92rem;
            font-weight: 600;
            color: #e2e8f0;
            margin: 0;
        }
        .result-section-header .badge-count {
            margin-left: auto;
            background: rgba(99,102,241,0.15);
            color: #a5b4fc;
            border: 1px solid rgba(99,102,241,0.3);
            border-radius: 999px;
            padding: 2px 10px;
            font-size: 0.73rem;
            font-weight: 600;
        }
        .result-section-body { padding: 16px 18px; }

        /* Risk Gauge */
        .risk-gauge-container {
            display: grid;
            grid-template-columns: auto 1fr;
            gap: 20px;
            align-items: center;
        }
        .risk-dial {
            position: relative;
            width: 120px;
            height: 120px;
            flex-shrink: 0;
        }
        .risk-dial svg { width: 100%; height: 100%; }
        .risk-dial .score-label {
            position: absolute;
            inset: 0;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
        }
        .risk-dial .score-value {
            font-size: 1.8rem;
            font-weight: 800;
            line-height: 1;
        }
        .risk-dial .score-unit { font-size: 0.7rem; color: #64748b; }
        .risk-details { flex: 1; }
        .risk-category-tag {
            display: inline-block;
            padding: 4px 14px;
            border-radius: 999px;
            font-size: 0.82rem;
            font-weight: 700;
            letter-spacing: 0.3px;
            margin-bottom: 10px;
        }
        .risk-category-tag.very-low { background: rgba(16,185,129,0.15); color: #34d399; border: 1px solid rgba(16,185,129,0.3); }
        .risk-category-tag.low      { background: rgba(16,185,129,0.12); color: #6ee7b7; border: 1px solid rgba(16,185,129,0.25); }
        .risk-category-tag.moderate { background: rgba(245,158,11,0.15); color: #fcd34d; border: 1px solid rgba(245,158,11,0.3); }
        .risk-category-tag.high     { background: rgba(239,68,68,0.15); color: #fca5a5; border: 1px solid rgba(239,68,68,0.3); }
        .risk-category-tag.critical { background: rgba(239,68,68,0.22); color: #f87171; border: 1px solid rgba(239,68,68,0.5); box-shadow: 0 0 16px rgba(239,68,68,0.15); }

        .risk-factor-bars { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
        .risk-factor {
            display: grid;
            grid-template-columns: 130px 1fr 36px;
            gap: 8px;
            align-items: center;
            font-size: 0.78rem;
        }
        .risk-factor .label { color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .risk-bar-track {
            height: 6px;
            background: var(--sim-panel-2);
            border-radius: 999px;
            overflow: hidden;
        }
        .risk-bar-fill {
            height: 100%;
            border-radius: 999px;
            transition: width 0.8s ease-out;
        }
        .risk-factor .val { color: #e2e8f0; font-weight: 500; text-align: right; }

        /* Factors list */
        .factors-list { display: flex; flex-direction: column; gap: 8px; }
        .factor-item {
            display: flex;
            gap: 10px;
            align-items: flex-start;
            font-size: 0.83rem;
            color: #94a3b8;
            padding: 8px 12px;
            background: var(--sim-panel-2);
            border-radius: 8px;
            border-left: 3px solid var(--sim-border);
        }
        .factor-item.warn { border-left-color: #f59e0b; }
        .factor-item.danger { border-left-color: #ef4444; }
        .factor-item.ok { border-left-color: #10b981; }
        .factor-item .fi-icon { flex-shrink: 0; }

        /* Affected components chips */
        .chips-container { display: flex; flex-wrap: wrap; gap: 8px; }
        .chip {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.73rem;
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 6px;
            padding: 4px 10px;
            color: #94a3b8;
            transition: all 0.15s;
        }
        .chip.direct { border-color: rgba(239,68,68,0.4); color: #fca5a5; background: rgba(239,68,68,0.08); }
        .chip.indirect { border-color: rgba(245,158,11,0.4); color: #fcd34d; background: rgba(245,158,11,0.06); }

        /* Metrics comparison table */
        .metrics-compare {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: 1px;
            background: var(--sim-border);
            border-radius: 10px;
            overflow: hidden;
        }
        .metrics-compare-cell {
            background: var(--sim-panel-2);
            padding: 14px 16px;
        }
        .metrics-compare-cell.header {
            background: #0f1420;
            font-size: 0.72rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: #64748b;
        }
        .metrics-compare-cell .mc-label { font-size: 0.78rem; color: #64748b; margin-bottom: 4px; }
        .metrics-compare-cell .mc-value { font-size: 1.1rem; font-weight: 700; color: #e2e8f0; }
        .metrics-compare-cell .mc-value.improved { color: #34d399; }
        .metrics-compare-cell .mc-value.worsened { color: #f87171; }
        .mc-delta { font-size: 0.72rem; margin-left: 4px; }
        .mc-delta.up { color: #f87171; }
        .mc-delta.down { color: #34d399; }

        /* Warning banners */
        .warning-item {
            background: rgba(245,158,11,0.08);
            border: 1px solid rgba(245,158,11,0.25);
            border-radius: 8px;
            padding: 10px 14px;
            font-size: 0.82rem;
            color: #fcd34d;
            display: flex;
            gap: 8px;
            align-items: flex-start;
        }

        /* Proposed operations */
        .op-list { display: flex; flex-direction: column; gap: 8px; }
        .op-item {
            display: flex;
            gap: 10px;
            align-items: flex-start;
            font-size: 0.83rem;
            color: #cbd5e1;
            padding: 10px 14px;
            background: var(--sim-panel-2);
            border-radius: 8px;
            border-left: 3px solid var(--sim-accent);
        }
        .op-step {
            background: var(--sim-accent);
            color: white;
            border-radius: 50%;
            width: 20px;
            height: 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 0.68rem;
            font-weight: 700;
            flex-shrink: 0;
        }

        /* Validation plan */
        .validation-item {
            display: flex;
            gap: 10px;
            align-items: flex-start;
            font-size: 0.82rem;
            color: #94a3b8;
            padding: 8px 0;
            border-bottom: 1px solid var(--sim-border);
        }
        .validation-item:last-child { border-bottom: none; }
        .validation-item .check-icon { color: var(--sim-teal); flex-shrink: 0; }

        /* Approval bar */
        .approval-bar {
            background: var(--sim-panel);
            border: 1px solid rgba(16,185,129,0.25);
            border-radius: 14px;
            padding: 16px 20px;
            display: flex;
            align-items: center;
            gap: 14px;
        }
        .approval-bar .info { flex: 1; }
        .approval-bar .info h4 { margin: 0 0 4px 0; font-size: 0.9rem; color: #e2e8f0; }
        .approval-bar .info p { margin: 0; font-size: 0.78rem; color: #64748b; }
        .approval-actions { display: flex; gap: 10px; }

        /* Simulation ID badge */
        .sim-id-badge {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.7rem;
            color: #475569;
            background: var(--sim-panel-2);
            padding: 3px 8px;
            border-radius: 4px;
            margin-left: auto;
        }

        /* Toast notification */
        #toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: var(--sim-panel);
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            padding: 14px 18px;
            font-size: 0.85rem;
            color: #e2e8f0;
            display: none;
            z-index: 1000;
            box-shadow: 0 8px 32px rgba(0,0,0,0.5);
            max-width: 360px;
            animation: slideInToast 0.3s ease;
        }
        @keyframes slideInToast { from { transform: translateX(20px); opacity: 0; } }
        #toast.success { border-color: rgba(16,185,129,0.5); }
        #toast.error   { border-color: rgba(239,68,68,0.5); }

        /* Dep paths */
        .dep-path {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.73rem;
            color: #94a3b8;
            padding: 6px 10px;
            background: var(--sim-panel-2);
            border-radius: 6px;
            word-break: break-all;
        }

        /* Unresolved questions */
        .question-item {
            display: flex;
            gap: 8px;
            align-items: flex-start;
            font-size: 0.82rem;
            color: #fcd34d;
            padding: 8px 12px;
            background: rgba(245,158,11,0.06);
            border-radius: 8px;
            border-left: 3px solid #f59e0b;
        }

        @media (max-width: 900px) {
            .sim-layout { grid-template-columns: 1fr; }
            .sim-input-panel { border-right: none; border-bottom: 1px solid var(--sim-border); }
        }

        /* Scrollbar */
        ::-webkit-scrollbar { width: 6px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: #1e2840; border-radius: 3px; }
    </style>
</head>
<body>

<header class="topbar">
    <div class="brand">⚡ Java ArchAnalyzer</div>
    <nav>
        <a href="<%= request.getContextPath() %>/projects/upload">New Analysis</a>
        <a href="<%= request.getContextPath() %>/dashboard" style="margin-left:24px;color:#94a3b8">Dashboard</a>
        <a href="<%= request.getContextPath() %>/projects/download/report" style="margin-left:24px;color:#94a3b8">Download Report</a>
    </nav>
</header>

<div class="sim-layout">
    <!-- ════════════════════════════════════
         LEFT PANEL — CHANGE INPUT FORM
    ════════════════════════════════════ -->
    <aside class="sim-input-panel">

        <div class="sim-panel-header">
            <h1>🔮 What-If Simulator</h1>
            <p class="subtitle">Describe a proposed architecture change. The engine will compute risk scores, blast-radius impact, and broken dependency warnings — without touching a single source file.</p>
        </div>

        <!-- Project info -->
        <div class="project-badge">
            <div class="icon">☕</div>
            <div class="info">
                <div class="name"><%= model.getProjectName() %></div>
                <div class="meta"><%= model.componentCount() %> components &bull; <%= model.fileCount() %> Java files</div>
            </div>
        </div>

        <!-- Form -->
        <form id="simForm" onsubmit="return false;">

            <div class="form-group">
                <label class="form-label" for="description">Describe the proposed change</label>
                <textarea id="description" name="description" class="sim-textarea"
                    placeholder="e.g. Add refund processing to PaymentService...&#10;e.g. Remove the deprecated SearchController endpoint&#10;e.g. Refactor OrderRepository to extract an interface"></textarea>
            </div>

            <!-- Quick example prompts -->
            <div class="form-group">
                <label class="form-label">Quick examples</label>
                <div class="quick-prompts" id="quickPrompts">
                    <% for (ProjectModel.ComponentNode cn : components.stream().limit(4).toList()) { %>
                    <button type="button" class="quick-prompt-btn"
                        data-desc="Refactor <%= cn.getSimpleName() %> to extract a dedicated interface"
                        data-target="<%= cn.getId() %>"
                        data-op="REFACTOR_COMPONENT"
                        onclick="applyQuickPrompt(this)">
                        Refactor <strong><%= cn.getSimpleName() %></strong> → extract interface
                    </button>
                    <% } %>
                    <button type="button" class="quick-prompt-btn"
                        data-desc="I want to introduce an interface between OrderService and PaymentService to reduce direct coupling."
                        data-op="INTRODUCE_INTERFACE"
                        onclick="applyQuickPrompt(this)">
                        Introduce interface between <strong>OrderService</strong> and <strong>PaymentService</strong>
                    </button>
                    <button type="button" class="quick-prompt-btn"
                        data-desc="Add a new caching module to the service layer"
                        data-op="ADD_MODULE"
                        onclick="applyQuickPrompt(this)">
                        Add caching module to service layer
                    </button>
                </div>
            </div>

            <!-- Target component -->
            <div class="form-group">
                <label class="form-label" for="target">Target component <span style="color:#475569;font-weight:400;text-transform:none">(optional — helps resolve ambiguity)</span></label>
                <select id="target" name="target" class="sim-select">
                    <option value="">— Auto-detect from description —</option>
                    <% for (ProjectModel.ComponentNode cn : components) { %>
                    <option value="<%= cn.getId() %>"><%= cn.getSimpleName() %> (<%= cn.getLayer() %>)</option>
                    <% } %>
                </select>
            </div>

            <!-- Operation type -->
            <div class="form-group">
                <label class="form-label" for="operation">Operation type <span style="color:#475569;font-weight:400;text-transform:none">(optional)</span></label>
                <select id="operation" name="operation" class="sim-select">
                    <option value="">— Auto-detect —</option>
                    <option value="ADD_FEATURE">➕ Add Feature</option>
                    <option value="ADD_MODULE">📦 Add Module</option>
                    <option value="REMOVE_FEATURE">🗑 Remove Feature</option>
                    <option value="REMOVE_API">🔌 Remove API</option>
                    <option value="MODIFY_API">🔧 Modify API Contract</option>
                    <option value="MODIFY_FEATURE">✏️ Modify Feature</option>
                    <option value="MODIFY_DATABASE_ENTITY">🗃 Modify Database Entity</option>
                    <option value="REFACTOR_COMPONENT">♻️ Refactor Component</option>
                    <option value="INTRODUCE_INTERFACE">🔌 Introduce Interface</option>
                    <option value="ADD_DEPENDENCY">➕ Add Dependency</option>
                    <option value="REMOVE_DEPENDENCY">➖ Remove Dependency</option>
                    <option value="REDIRECT_DEPENDENCY">↪ Redirect Dependency</option>
                    <option value="OTHER">⚙️ Other</option>
                </select>
            </div>

            <button type="button" class="btn-simulate" id="simBtn" onclick="runSimulation()">
                <span id="simBtnIcon">⚡</span>
                <span id="simBtnText">Run What-If Simulation</span>
            </button>
        </form>

        <!-- Approve/Reset controls (shown after simulation) -->
        <div id="approvalControls" style="display:none;">
            <div style="border-top:1px solid var(--sim-border);padding-top:16px;display:flex;flex-direction:column;gap:10px;">
                <div style="font-size:0.78rem;color:#64748b;font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Simulation Complete</div>
                <button class="btn-approve" id="approveBtn" onclick="approveChange()">
                    ✅ Approve &amp; Apply Change
                </button>
                <button class="btn-secondary" onclick="resetSimulator()">
                    🔄 New Simulation
                </button>
                <a href="<%= request.getContextPath() %>/projects/download/report" class="btn-secondary" style="text-decoration:none;text-align:center">
                    📄 Download Full Report
                </a>
            </div>
        </div>

    </aside>

    <!-- ════════════════════════════════════
         RIGHT PANEL — SIMULATION RESULTS
    ════════════════════════════════════ -->
    <section class="sim-results-panel" id="resultsPanel">

        <!-- EMPTY STATE -->
        <div class="state-empty" id="emptyState">
            <div class="big-icon">🧪</div>
            <h2>No simulation yet</h2>
            <p>Describe a proposed architecture change on the left and click <strong>Run What-If Simulation</strong>. The engine will analyze impact across <strong><%= model.componentCount() %> components</strong> without modifying any source files.</p>

            <div style="margin-top:32px;display:grid;grid-template-columns:1fr 1fr;gap:12px;text-align:left;max-width:440px;">
                <div style="background:#11151f;border:1px solid #1e2840;border-radius:10px;padding:14px;">
                    <div style="font-size:1.3rem;margin-bottom:6px;">📊</div>
                    <div style="font-size:0.82rem;font-weight:600;color:#e2e8f0;margin-bottom:4px;">Risk Score 0–100</div>
                    <div style="font-size:0.75rem;color:#64748b;">ML + heuristic blend</div>
                </div>
                <div style="background:#11151f;border:1px solid #1e2840;border-radius:10px;padding:14px;">
                    <div style="font-size:1.3rem;margin-bottom:6px;">💥</div>
                    <div style="font-size:0.82rem;font-weight:600;color:#e2e8f0;margin-bottom:4px;">Blast-Radius Map</div>
                    <div style="font-size:0.75rem;color:#64748b;">Direct &amp; indirect dependents</div>
                </div>
                <div style="background:#11151f;border:1px solid #1e2840;border-radius:10px;padding:14px;">
                    <div style="font-size:1.3rem;margin-bottom:6px;">⚠️</div>
                    <div style="font-size:0.82rem;font-weight:600;color:#e2e8f0;margin-bottom:4px;">Broken Dep Warnings</div>
                    <div style="font-size:0.75rem;color:#64748b;">Before they happen</div>
                </div>
                <div style="background:#11151f;border:1px solid #1e2840;border-radius:10px;padding:14px;">
                    <div style="font-size:1.3rem;margin-bottom:6px;">📈</div>
                    <div style="font-size:0.82rem;font-weight:600;color:#e2e8f0;margin-bottom:4px;">Metric Projection</div>
                    <div style="font-size:0.75rem;color:#64748b;">Before vs. after comparison</div>
                </div>
            </div>
        </div>

        <!-- LOADING STATE -->
        <div class="sim-loading" id="loadingState">
            <div class="sim-spinner"></div>
            <strong style="color:#e2e8f0;font-size:1rem;">Running Architecture Simulation...</strong>
            <div class="loading-text">Analyzing impact across <%= model.componentCount() %> components</div>
            <div class="loading-steps">
                <span>🔍 Parsing change proposal...</span>
                <span>🕸 Traversing dependency graph...</span>
                <span>⚡ Computing blast-radius impact...</span>
                <span>🤖 Running ML risk assessment...</span>
            </div>
        </div>

        <!-- RESULT CONTENT -->
        <div class="result-grid" id="resultGrid">

            <div class="result-section" id="planSection">
                <div class="result-section-header">
                    <span class="icon">🗂</span>
                    <h3>Interpreted Change Plan (simulation only — sources untouched)</h3>
                </div>
                <div class="result-section-body" id="planBody"></div>
            </div>

            <div class="result-section" id="graphCompareSection">
                <div class="result-section-header">
                    <span class="icon">🕸</span>
                    <h3>Before / After Dependency Graph</h3>
                </div>
                <div class="result-section-body">
                    <div class="graph-tools" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">
                        <button type="button" class="btn-secondary" onclick="showGraphMode('side')">Side by side</button>
                        <button type="button" class="btn-secondary" onclick="showGraphMode('proposed')">Proposed only</button>
                        <button type="button" class="btn-secondary" onclick="resetSimGraphs()">Reset view</button>
                    </div>
                    <div id="graphPair" style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                        <div>
                            <div style="font-size:0.75rem;color:#64748b;margin-bottom:6px">CURRENT</div>
                            <canvas id="graphBefore" style="width:100%;height:320px;background:#0a0d14;border:1px solid #1e2840;border-radius:10px"></canvas>
                        </div>
                        <div>
                            <div style="font-size:0.75rem;color:#64748b;margin-bottom:6px">PROPOSED (added = green, removed = dashed red)</div>
                            <canvas id="graphAfter" style="width:100%;height:320px;background:#0a0d14;border:1px solid #1e2840;border-radius:10px"></canvas>
                        </div>
                    </div>
                    <div id="edgeDelta" style="margin-top:12px;font-size:0.82rem;color:#94a3b8"></div>
                </div>
            </div>

            <div class="result-section" id="altSection">
                <div class="result-section-header">
                    <span class="icon">🔁</span>
                    <h3>Alternative Designs</h3>
                </div>
                <div class="result-section-body" id="altBody"></div>
            </div>

            <!-- ① Risk Assessment -->
            <div class="result-section" id="riskSection">
                <div class="result-section-header">
                    <span class="icon">📊</span>
                    <h3>Risk Assessment</h3>
                    <span class="sim-id-badge" id="simIdBadge"></span>
                </div>
                <div class="result-section-body">
                    <div class="risk-gauge-container">
                        <div class="risk-dial">
                            <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <!-- Track -->
                                <circle cx="60" cy="60" r="48" stroke="#1e2840" stroke-width="10" fill="none"
                                    stroke-dasharray="251.3" stroke-dashoffset="62.8"
                                    stroke-linecap="round" transform="rotate(135 60 60)"/>
                                <!-- Fill -->
                                <circle cx="60" cy="60" r="48" id="riskArc" stroke="#6366f1" stroke-width="10" fill="none"
                                    stroke-dasharray="251.3" stroke-dashoffset="251.3"
                                    stroke-linecap="round" transform="rotate(135 60 60)"
                                    style="transition: stroke-dashoffset 1s ease-out, stroke 0.5s"/>
                            </svg>
                            <div class="score-label">
                                <div class="score-value" id="riskScore" style="color:#6366f1">–</div>
                                <div class="score-unit">/ 100</div>
                            </div>
                        </div>
                        <div class="risk-details">
                            <div class="risk-category-tag" id="riskCategoryTag">Loading…</div>
                            <div style="font-size:0.8rem;color:#64748b;margin-bottom:12px;" id="riskConfidence"></div>
                            <!-- Factor bars -->
                            <div class="risk-factor-bars" id="riskFactorBars"></div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- ② Contributing Factors -->
            <div class="result-section" id="factorsSection">
                <div class="result-section-header">
                    <span class="icon">🔬</span>
                    <h3>Contributing Risk Factors</h3>
                    <span class="badge-count" id="factorsCount">0</span>
                </div>
                <div class="result-section-body">
                    <div class="factors-list" id="factorsList"></div>
                </div>
            </div>

            <!-- ③ Blast Radius -->
            <div class="result-section" id="blastSection">
                <div class="result-section-header">
                    <span class="icon">💥</span>
                    <h3>Blast-Radius Impact</h3>
                    <span class="badge-count" id="blastCount">0 affected</span>
                </div>
                <div class="result-section-body" id="blastBody">
                    <!-- injected by JS -->
                </div>
            </div>

            <!-- ④ Proposed Operations -->
            <div class="result-section" id="opsSection">
                <div class="result-section-header">
                    <span class="icon">🛠</span>
                    <h3>Proposed Implementation Steps</h3>
                </div>
                <div class="result-section-body">
                    <div class="op-list" id="opsList"></div>
                </div>
            </div>

            <!-- ⑤ Warnings & Cycles -->
            <div class="result-section" id="warningsSection" style="display:none;">
                <div class="result-section-header">
                    <span class="icon">⚠️</span>
                    <h3>Compatibility Warnings</h3>
                    <span class="badge-count" id="warnCount">0</span>
                </div>
                <div class="result-section-body">
                    <div id="warnList" style="display:flex;flex-direction:column;gap:8px;"></div>
                </div>
            </div>

            <!-- ⑥ Metrics Comparison -->
            <div class="result-section" id="metricsSection">
                <div class="result-section-header">
                    <span class="icon">📈</span>
                    <h3>Architecture Metrics — Before vs. After Projection</h3>
                </div>
                <div class="result-section-body">
                    <div class="metrics-compare" id="metricsCompare"></div>
                </div>
            </div>

            <!-- ⑦ Unresolved Questions -->
            <div class="result-section" id="questionsSection" style="display:none;">
                <div class="result-section-header">
                    <span class="icon">❓</span>
                    <h3>Unresolved Design Decisions</h3>
                </div>
                <div class="result-section-body">
                    <div id="questionsList" style="display:flex;flex-direction:column;gap:8px;"></div>
                </div>
            </div>

            <!-- ⑧ Validation Plan -->
            <div class="result-section" id="validationSection">
                <div class="result-section-header">
                    <span class="icon">✅</span>
                    <h3>Recommended Validation Plan</h3>
                </div>
                <div class="result-section-body">
                    <div id="validationList"></div>
                </div>
            </div>

            <!-- ⑨ Approve / Implement -->
            <div class="approval-bar" id="approvalBar">
                <div style="font-size:1.6rem;">🚀</div>
                <div class="info">
                    <h4>Ready to Apply This Change?</h4>
                    <p>Approving will create an isolated copy, apply a <strong>supported</strong> source transformation, validate compilation, and package a ZIP. The uploaded original is never overwritten. Unsupported operations stay simulated-only.</p>
                    <p id="applyLimitation" style="margin-top:8px;color:#fcd34d;font-size:0.78rem"></p>
                </div>
                <div class="approval-actions">
                    <button class="btn-approve" id="approveBarBtn" onclick="approveChange()">
                        ✅ Approve &amp; Apply
                    </button>
                    <a id="downloadZipLink" href="<%= request.getContextPath() %>/projects/download/zip" class="btn-secondary" style="display:none;text-decoration:none">Download Refactored Project</a>
                </div>
            </div>

        </div><!-- /result-grid -->

    </section>
</div>

<!-- Toast -->
<div id="toast"></div>

<script src="<%= request.getContextPath() %>/js/graph.js"></script>
<script>
/* ═══════════════════════════════════════
   WHAT-IF SIMULATOR — FRONTEND ENGINE
═══════════════════════════════════════ */
const CTX = '<%= request.getContextPath() %>';
let lastSimulation = null;
let graphBefore, graphAfter;

// ─── Quick Prompt ───
function applyQuickPrompt(btn) {
    document.getElementById('description').value = btn.dataset.desc || '';
    if (btn.dataset.target) {
        document.getElementById('target').value = btn.dataset.target;
    }
    if (btn.dataset.op) {
        document.getElementById('operation').value = btn.dataset.op;
    }
    document.getElementById('description').focus();
}

// ─── Run Simulation ───
async function runSimulation() {
    const desc = document.getElementById('description').value.trim();
    if (!desc) {
        showToast('Please describe the proposed change first.', 'error');
        document.getElementById('description').focus();
        return;
    }

    // UI transitions
    setUiState('loading');
    const btn = document.getElementById('simBtn');
    btn.disabled = true;

    const body = new URLSearchParams({
        description: desc,
        target:      document.getElementById('target').value,
        operation:   document.getElementById('operation').value,
        alternativeId: lastSimulation && lastSimulation._pendingAlt ? lastSimulation._pendingAlt : ''
    });

    try {
        const res  = await fetch(CTX + '/simulator', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString()
        });
        const data = await res.json();

        if (!res.ok || data.error) {
            throw new Error(data.error || 'Simulation request failed (' + res.status + ')');
        }

        lastSimulation = data.simulation;
        renderResults(lastSimulation);
        setUiState('results');
        showToast('Simulation complete!', 'success');

    } catch (e) {
        setUiState('empty');
        showToast('Error: ' + e.message, 'error');
    } finally {
        btn.disabled = false;
    }
}

// ─── Approve & Apply ───
async function approveChange() {
    if (!lastSimulation) return;

    const btns = document.querySelectorAll('#approveBtn, #approveBarBtn');
    btns.forEach(b => { b.disabled = true; b.textContent = '⏳ Applying...'; });

    try {
        const res  = await fetch(CTX + '/simulator/approve', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });
        const data = await res.json();

        if (!res.ok || data.error) throw new Error(data.error || 'Approval failed');

        const msg = data.success
            ? '✅ Change applied! Download the updated project ZIP from the Download Report link.'
            : (data.rolledBack ? '⚠️ Change was attempted but rolled back due to build errors.' : '❌ Implementation failed.');
        showToast(msg, data.success ? 'success' : 'error');

        if (data.success) {
            document.getElementById('approvalBar').style.background = 'rgba(16,185,129,0.1)';
            document.getElementById('approvalBar').style.borderColor = 'rgba(16,185,129,0.4)';
            btns.forEach(b => { b.textContent = '✅ Applied!'; b.style.background = '#064e3b'; });
            const dl = document.getElementById('downloadZipLink');
            dl.style.display = 'inline-flex';
            const note = document.getElementById('applyLimitation');
            note.style.color = '#6ee7b7';
            note.textContent = (data.summary || 'Applied.') + ' Static validation: ' + (data.staticValidation || 'n/a')
                + '. Compiler: ' + (data.compilerStatus || 'n/a')
                + '. Full Maven/Gradle build of the upload was not executed.';
        } else {
            btns.forEach(b => { b.disabled = false; b.textContent = data.rolledBack ? '⚠️ Rolled Back' : '✅ Retry'; });
            document.getElementById('applyLimitation').textContent = data.summary || 'Implementation did not succeed.';
        }
    } catch (e) {
        showToast('Approval error: ' + e.message, 'error');
        btns.forEach(b => { b.disabled = false; b.textContent = '✅ Approve & Apply'; });
    }
}

// ─── Reset ───
function resetSimulator() {
    lastSimulation = null;
    document.getElementById('description').value = '';
    document.getElementById('target').value = '';
    document.getElementById('operation').value = '';
    setUiState('empty');
    document.getElementById('approvalControls').style.display = 'none';
    document.querySelectorAll('#approveBtn,#approveBarBtn').forEach(b => {
        b.disabled = false;
        b.textContent = '✅ Approve & Apply';
    });
}

// ─── UI State Machine ───
function setUiState(state) {
    document.getElementById('emptyState').style.display   = state === 'empty'   ? '' : 'none';
    document.getElementById('loadingState').classList[state === 'loading' ? 'add' : 'remove']('visible');
    document.getElementById('resultGrid').classList[state === 'results' ? 'add' : 'remove']('visible');
    document.getElementById('approvalControls').style.display = state === 'results' ? 'block' : 'none';
}

// ─── Render Results ───
function renderResults(sim) {
    const req  = sim.request  || {};
    const risk = sim.risk     || {};

    document.getElementById('simIdBadge').textContent = sim.simulationId || '';
    renderPlan(sim);
    renderGraphs(sim);
    renderAlternatives(sim);
    const applyNote = document.getElementById('applyLimitation');
    if (sim.autoApplicable === false) {
        applyNote.textContent = sim.unsupportedReason || 'This operation is simulated on the graph but is not a supported automated source rewrite.';
        document.getElementById('approveBarBtn').disabled = true;
        document.getElementById('approveBtn').disabled = true;
    } else {
        applyNote.textContent = '';
        document.getElementById('approveBarBtn').disabled = false;
        document.getElementById('approveBtn').disabled = false;
    }

    // ① Risk Gauge
    const score    = risk.overallScore || 0;
    const category = (risk.riskCategory || 'Unknown').toLowerCase().replace(' ', '-');
    document.getElementById('riskScore').textContent = Math.round(score);
    const riskColor = getRiskColor(score);
    document.getElementById('riskScore').style.color = riskColor;

    const arc    = document.getElementById('riskArc');
    const arcLen = 251.3 * 0.75;
    const offset = arcLen - (arcLen * score / 100);
    arc.style.strokeDashoffset = offset;
    arc.style.stroke = riskColor;

    const catTag = document.getElementById('riskCategoryTag');
    catTag.textContent = risk.riskCategory || 'Unknown';
    catTag.className   = 'risk-category-tag ' + category;

    document.getElementById('riskConfidence').textContent =
        'Confidence: ' + (risk.confidence != null ? Math.round(risk.confidence) + '%' : '—') +
        (risk.mlPredictedScore != null ? ' · ML Score: ' + risk.mlPredictedScore + ' / 100' : '');

    // Factor bars
    const fw = risk.factorWeights || {};
    const fbarContainer = document.getElementById('riskFactorBars');
    fbarContainer.innerHTML = '';
    const barColors = {
        downstreamBreadth:     '#6366f1',
        impactDepth:           '#8b5cf6',
        couplingAndCentrality: '#f59e0b',
        cycleInvolvement:      '#ef4444',
        operationAndLayer:     '#0ea5e9',
        testCoverageDeficit:   '#f97316',
    };
    const barLabels = {
        downstreamBreadth:     'Downstream Breadth',
        impactDepth:           'Impact Depth',
        couplingAndCentrality: 'Coupling & Centrality',
        cycleInvolvement:      'Cycle Involvement',
        operationAndLayer:     'Operation Criticality',
        testCoverageDeficit:   'Test Coverage Deficit',
    };
    const maxW = 25;
    Object.entries(fw).forEach(([k, v]) => {
        const label = barLabels[k] || k;
        const color = barColors[k] || '#6366f1';
        const pct   = Math.min(100, (v / maxW) * 100);
        fbarContainer.innerHTML += `
            <div class="risk-factor">
                <span class="label">${escHtml(label)}</span>
                <div class="risk-bar-track">
                    <div class="risk-bar-fill" style="width:${pct}%;background:${color}"></div>
                </div>
                <span class="val">${v}</span>
            </div>`;
    });

    // ② Contributing Factors
    const factors = risk.contributingFactors || [];
    document.getElementById('factorsCount').textContent = factors.length;
    const fl = document.getElementById('factorsList');
    fl.innerHTML = '';
    factors.forEach(f => {
        const isWarn   = /cycle|high coupling|removal|removed|break/.test(f.toLowerCase());
        const isDanger = /cycle|broken|removed/.test(f.toLowerCase());
        const isOk     = /low|automated unit test/.test(f.toLowerCase());
        const cls = isDanger ? 'danger' : (isWarn ? 'warn' : (isOk ? 'ok' : ''));
        const icon = isDanger ? '🔴' : (isWarn ? '🟡' : (isOk ? '🟢' : '🔵'));
        fl.innerHTML += `<div class="factor-item ${cls}"><span class="fi-icon">${icon}</span><span>${escHtml(f)}</span></div>`;
    });

    // ③ Blast Radius
    const direct   = sim.directlyAffected   || [];
    const indirect = sim.indirectlyAffected || [];
    const total    = direct.length + indirect.length;
    document.getElementById('blastCount').textContent = total + ' affected';

    const blastBody = document.getElementById('blastBody');
    blastBody.innerHTML = '';

    if (total === 0) {
        blastBody.innerHTML = '<div style="color:#64748b;font-size:0.85rem;padding:8px 0;">✅ No downstream components affected. This component is an isolated leaf node.</div>';
    } else {
        if (direct.length > 0) {
            blastBody.innerHTML += `<div style="font-size:0.75rem;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;color:#ef4444;margin-bottom:8px;">Direct (${direct.length})</div>`;
            blastBody.innerHTML += `<div class="chips-container">${direct.map(c => `<span class="chip direct">${escHtml(shortName(c))}</span>`).join('')}</div>`;
        }
        if (indirect.length > 0) {
            blastBody.innerHTML += `<div style="font-size:0.75rem;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;color:#f59e0b;margin-top:14px;margin-bottom:8px;">Indirect (${indirect.length})</div>`;
            blastBody.innerHTML += `<div class="chips-container">${indirect.map(c => `<span class="chip indirect">${escHtml(shortName(c))}</span>`).join('')}</div>`;
        }
        // Dep paths
        const paths = sim.dependencyPaths || {};
        const pathEntries = Object.entries(paths).slice(0, 5);
        if (pathEntries.length > 0) {
            blastBody.innerHTML += `<div style="font-size:0.75rem;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;color:#64748b;margin-top:16px;margin-bottom:8px;">Dependency Paths (top ${pathEntries.length})</div>`;
            pathEntries.forEach(([k, path]) => {
                blastBody.innerHTML += `<div class="dep-path" style="margin-bottom:5px;">${Array.isArray(path) ? path.map(p => escHtml(shortName(p))).join(' → ') : escHtml(String(path))}</div>`;
            });
        }
    }

    // ④ Proposed Operations
    const ops = req.proposedOperations || [];
    const opsList = document.getElementById('opsList');
    opsList.innerHTML = '';
    if (ops.length === 0) {
        opsList.innerHTML = '<div style="color:#64748b;font-size:0.85rem;">No structured operations defined for this change type.</div>';
    } else {
        ops.forEach((op, i) => {
            opsList.innerHTML += `<div class="op-item"><div class="op-step">${i+1}</div><span>${escHtml(op)}</span></div>`;
        });
    }

    // ⑤ Warnings & Cycles
    const broken = sim.potentiallyBrokenDependencies || [];
    const cycles = sim.potentialCycles || [];
    const allWarns = [...broken, ...cycles];
    const warnSection = document.getElementById('warningsSection');
    if (allWarns.length > 0) {
        warnSection.style.display = '';
        document.getElementById('warnCount').textContent = allWarns.length;
        const wl = document.getElementById('warnList');
        wl.innerHTML = '';
        allWarns.forEach(w => {
            wl.innerHTML += `<div class="warning-item">⚠️ <span>${escHtml(w)}</span></div>`;
        });
    } else {
        warnSection.style.display = 'none';
    }

    // ⑥ Metrics
    const bm = sim.baselineMetrics  || {};
    const pm = sim.projectedMetrics || {};
    const metricsCompare = document.getElementById('metricsCompare');
    const metricDefs = [
        { key: 'architectureHealth', label: 'Architecture Health', unit: '/ 100', lowerIsBad: false },
        { key: 'dependencyScore',    label: 'Dependency Pressure', unit: '/ 100', lowerIsBad: false },
        { key: 'cycleCount',         label: 'Circular Dependencies', unit: '',     lowerIsBad: true },
        { key: 'totalComponents',    label: 'Total Components',    unit: '',     lowerIsBad: null },
        { key: 'totalDependencies',  label: 'Total Dependencies',  unit: '',     lowerIsBad: null },
        { key: 'averageFanOut',      label: 'Average fan-out',     unit: '',     lowerIsBad: true },
        { key: 'maxFanOut',          label: 'Maximum fan-out',     unit: '',     lowerIsBad: true },
    ];
    metricsCompare.innerHTML = `
        <div class="metrics-compare-cell header">Metric</div>
        <div class="metrics-compare-cell header" style="text-align:center">Baseline</div>
        <div class="metrics-compare-cell header" style="text-align:center">Projected</div>`;
    metricDefs.forEach(m => {
        const base = bm[m.key] != null ? round1(bm[m.key]) : '—';
        const proj = pm[m.key] != null ? round1(pm[m.key]) : '—';
        let cls = '', delta = '';
        if (bm[m.key] != null && pm[m.key] != null && m.lowerIsBad !== null) {
            const diff = pm[m.key] - bm[m.key];
            if (Math.abs(diff) > 0.05) {
                const improved = m.lowerIsBad ? diff < 0 : diff > 0;
                cls   = improved ? 'improved' : 'worsened';
                delta = `<span class="mc-delta ${improved ? 'down' : 'up'}">(${diff > 0 ? '+' : ''}${round1(diff)})</span>`;
            }
        }
        metricsCompare.innerHTML += `
            <div class="metrics-compare-cell"><div class="mc-label">${m.label}</div></div>
            <div class="metrics-compare-cell" style="text-align:center"><div class="mc-value">${base} ${m.unit}</div></div>
            <div class="metrics-compare-cell" style="text-align:center"><div class="mc-value ${cls}">${proj} ${m.unit}${delta}</div></div>`;
    });

    // ⑦ Unresolved Questions
    const questions = req.unresolvedQuestions || [];
    const qSection  = document.getElementById('questionsSection');
    if (questions.length > 0) {
        qSection.style.display = '';
        const ql = document.getElementById('questionsList');
        ql.innerHTML = '';
        questions.forEach(q => {
            ql.innerHTML += `<div class="question-item">❓ <span>${escHtml(q)}</span></div>`;
        });
    } else {
        qSection.style.display = 'none';
    }

    // ⑧ Validation Plan
    const plan = req.validationPlan || [];
    const vl   = document.getElementById('validationList');
    vl.innerHTML = '';
    plan.forEach(step => {
        vl.innerHTML += `<div class="validation-item"><span class="check-icon">✓</span><span>${escHtml(step)}</span></div>`;
    });
    if (plan.length === 0) {
        vl.innerHTML = '<div style="color:#64748b;font-size:0.85rem;">No validation plan generated.</div>';
    }
}

// ─── Helpers ───
function getRiskColor(score) {
    if (score >= 80) return '#ef4444';
    if (score >= 60) return '#f97316';
    if (score >= 40) return '#f59e0b';
    if (score >= 20) return '#10b981';
    return '#6ee7b7';
}

function shortName(fqn) {
    if (!fqn) return '';
    const parts = fqn.split(/[./]/);
    return parts[parts.length - 1] || fqn;
}

function escHtml(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function round1(v) { return Math.round(Number(v) * 10) / 10; }

function renderPlan(sim) {
    const plan = sim.changePlan || {};
    const req = sim.request || {};
    const body = document.getElementById('planBody');
    const added = (plan.addedDependencies || []).map(e => escHtml(shortName(e.from)) + ' → ' + escHtml(shortName(e.to))).join(', ') || 'none';
    const removed = (plan.removedDependencies || []).map(e => escHtml(shortName(e.from)) + ' → ' + escHtml(shortName(e.to))).join(', ') || 'none';
    body.innerHTML = `
        <p style="color:#cbd5e1;margin-top:0"><strong>${escHtml(plan.interpretedRequirement || req.structuredSummary || '')}</strong></p>
        <p style="font-size:0.8rem;color:#34d399">Simulation does not modify extracted sources. originalModelMutated=${sim.originalModelMutated === true}</p>
        <div class="op-list">
            <div class="op-item">Operation: ${escHtml(plan.operation || req.operation || '')}</div>
            <div class="op-item">Primary: ${escHtml(plan.primaryComponentId || req.targetComponentId || 'n/a')}</div>
            <div class="op-item">Secondary: ${escHtml(plan.secondaryComponentId || req.secondaryComponentId || 'n/a')}</div>
            <div class="op-item">New type: ${escHtml(plan.newComponentId || 'n/a')}</div>
            <div class="op-item">Added edges: ${added}</div>
            <div class="op-item">Removed edges: ${removed}</div>
        </div>`;
}

function renderGraphs(sim) {
    const beforeCanvas = document.getElementById('graphBefore');
    const afterCanvas = document.getElementById('graphAfter');
    graphBefore = ArchGraph.createGraph(beforeCanvas, {});
    graphAfter = ArchGraph.createGraph(afterCanvas, {});
    graphBefore.load(sim.baselineGraph || {nodes:[], edges:[]});
    graphAfter.load(sim.proposedGraph || {nodes:[], edges:[]});
    const plan = sim.changePlan || {};
    const added = plan.addedDependencies || [];
    const removed = plan.removedDependencies || [];
    document.getElementById('edgeDelta').innerHTML =
        '<strong>Added:</strong> ' + (added.map(e => escHtml(shortName(e.from)+' → '+shortName(e.to))).join(', ') || 'none')
        + '<br><strong>Removed:</strong> ' + (removed.map(e => escHtml(shortName(e.from)+' → '+shortName(e.to))).join(', ') || 'none')
        + '<br><strong>New components:</strong> ' + ((plan.createdComponents||[]).map(escHtml).join(', ') || 'none');
}

function showGraphMode(mode) {
    const pair = document.getElementById('graphPair');
    pair.style.gridTemplateColumns = mode === 'proposed' ? '1fr' : '1fr 1fr';
    pair.firstElementChild.style.display = mode === 'proposed' ? 'none' : '';
}
function resetSimGraphs() {
    if (graphBefore) graphBefore.resetView();
    if (graphAfter) graphAfter.resetView();
}

function renderAlternatives(sim) {
    const alts = (sim.changePlan && sim.changePlan.alternatives) || [];
    const body = document.getElementById('altBody');
    if (!alts.length) {
        body.innerHTML = '<p style="color:#64748b;font-size:0.85rem;margin:0">No additional designs were generated for this request.</p>';
        return;
    }
    body.innerHTML = alts.map(a => `
        <div class="factor-item ${a.recommended ? 'ok' : ''}" style="flex-direction:column;align-items:stretch">
            <div><strong>${escHtml(a.title)}</strong> ${a.recommended ? '· recommended' : ''} · complexity ${escHtml(a.complexity)} · risk ${escHtml(a.predictedRisk)}</div>
            <div>${escHtml(a.design)}</div>
            <div style="font-size:0.78rem;color:#94a3b8">Benefits: ${escHtml(a.benefits)} · Drawbacks: ${escHtml(a.disadvantages)}</div>
            <button type="button" class="btn-secondary" style="margin-top:8px;width:auto" onclick="simulateAlternative('${escHtml(a.id)}')">Simulate this alternative</button>
        </div>`).join('');
}

function simulateAlternative(id) {
    if (!lastSimulation) return;
    lastSimulation._pendingAlt = id;
    runSimulation();
}

function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className   = type || '';
    t.style.display = 'block';
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.style.display = 'none'; }, 4500);
}

// Keyboard shortcut: Ctrl+Enter to run simulation
document.getElementById('description').addEventListener('keydown', e => {
    if (e.ctrlKey && e.key === 'Enter') runSimulation();
});
</script>
</body>
</html>
