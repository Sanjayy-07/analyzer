<%@ page contentType="text/html;charset=UTF-8" language="java" isELIgnored="true" %>
<%@ page import="com.archanalyzer.web.history.ProjectHistoryManager.HistoryEntry" %>
<%@ page import="com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult" %>
<%@ page import="java.util.List" %>
<%
    List<HistoryEntry> historyList = (List<HistoryEntry>) request.getAttribute("historyList");
    if (historyList == null) {
        historyList = com.archanalyzer.web.history.ProjectHistoryManager.getHistory();
    }
    PipelineResult activeResult = (PipelineResult) request.getAttribute("activeResult");
    String activeName = (activeResult != null && activeResult.model != null) ? activeResult.model.getProjectName() : null;
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Project Analysis History — Java ArchAnalyzer</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/main.css">
    <style>
        .history-container {
            max-width: 1200px;
            margin: 32px auto;
            padding: 0 24px;
        }
        .history-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
            margin-bottom: 28px;
            padding-bottom: 20px;
            border-bottom: 1px solid var(--border);
        }
        .history-header h1 {
            margin: 0 0 6px 0;
            font-size: 1.8rem;
            color: #f1f5f9;
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .history-header p {
            margin: 0;
            color: var(--text-dim);
            font-size: 0.92rem;
        }
        .history-card {
            background: var(--card-bg, #0e1320);
            border: 1px solid var(--border, #1e2840);
            border-radius: 12px;
            padding: 20px 24px;
            margin-bottom: 18px;
            transition: border-color 0.2s, transform 0.15s, box-shadow 0.2s;
        }
        .history-card:hover {
            border-color: rgba(99, 102, 241, 0.45);
            box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
        }
        .history-card.active-card {
            border: 1px solid rgba(56, 189, 248, 0.5);
            background: linear-gradient(135deg, rgba(14, 19, 32, 0.95), rgba(15, 23, 42, 0.85));
            box-shadow: 0 0 16px rgba(56, 189, 248, 0.15);
        }
        .card-top {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 16px;
        }
        .proj-title {
            font-size: 1.25rem;
            font-weight: 700;
            color: #f8fafc;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .proj-meta {
            font-size: 0.82rem;
            color: #64748b;
            margin-top: 4px;
        }
        .metrics-pill-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
            gap: 12px;
            background: rgba(0, 0, 0, 0.25);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 8px;
            padding: 12px 16px;
            margin-bottom: 18px;
        }
        .metric-item {
            display: flex;
            flex-direction: column;
        }
        .metric-item .lbl {
            font-size: 0.72rem;
            color: #64748b;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 3px;
        }
        .metric-item .val {
            font-size: 1.05rem;
            font-weight: 700;
            color: #e2e8f0;
        }
        .val.health { color: #38bdf8; }
        .val.cycles { color: #ef4444; }
        .val.smells { color: #f59e0b; }
        .card-actions {
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 10px;
            padding-top: 14px;
            border-top: 1px solid rgba(255, 255, 255, 0.06);
        }
        .action-btns-left {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        .btn-view-analysis {
            background: linear-gradient(135deg, #6366f1, #4f46e5);
            color: #fff;
            padding: 8px 18px;
            border-radius: 8px;
            text-decoration: none;
            font-weight: 600;
            font-size: 0.88rem;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: opacity 0.2s;
        }
        .btn-view-analysis:hover { opacity: 0.92; }
        .btn-sim {
            background: rgba(168, 85, 247, 0.15);
            color: #c084fc;
            border: 1px solid rgba(168, 85, 247, 0.35);
            padding: 8px 16px;
            border-radius: 8px;
            text-decoration: none;
            font-weight: 600;
            font-size: 0.88rem;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .btn-sim:hover { background: rgba(168, 85, 247, 0.25); }
        .btn-delete {
            background: transparent;
            color: #94a3b8;
            border: 1px solid transparent;
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 0.82rem;
            cursor: pointer;
            text-decoration: none;
        }
        .btn-delete:hover { color: #f87171; border-color: rgba(239, 68, 68, 0.3); }
        .empty-history {
            text-align: center;
            padding: 70px 20px;
            background: var(--card-bg, #0e1320);
            border: 1px dashed var(--border, #1e2840);
            border-radius: 12px;
        }
        .empty-history .icon { font-size: 3rem; margin-bottom: 14px; }
        .empty-history h3 { font-size: 1.25rem; color: #f1f5f9; margin-bottom: 8px; }
        .empty-history p { color: #64748b; font-size: 0.9rem; margin-bottom: 22px; max-width: 480px; margin-left: auto; margin-right: auto; }
    </style>
</head>
<body>

<header class="topbar">
    <a href="<%= request.getContextPath() %>/" style="text-decoration:none;color:inherit"><div class="brand">⚡ Java ArchAnalyzer</div></a>
    <nav>
        <a href="<%= request.getContextPath() %>/projects/upload">New Analysis</a>
        <a href="<%= request.getContextPath() %>/dashboard" style="margin-left:20px;color:#94a3b8">Dashboard</a>
        <a href="<%= request.getContextPath() %>/simulator" style="margin-left:20px;color:#a5b4fc;font-weight:600">🔮 What-If Simulator</a>
        <a href="<%= request.getContextPath() %>/history" style="margin-left:20px;background:rgba(56,189,248,0.15);color:#38bdf8;border:1px solid rgba(56,189,248,0.4);padding:5px 12px;border-radius:6px;font-weight:600;text-decoration:none;display:inline-flex;align-items:center;gap:6px">
            🕒 View History
        </a>
    </nav>
</header>

<main class="history-container">
    <div class="history-header">
        <div>
            <h1><span>🕒</span> Project Analysis History</h1>
            <p>Review previously uploaded and analyzed Java project archives. Click <strong>View Dynamic Analysis</strong> to open a project's interactive dependency graph, metrics, smell diagnoses, and refactoring candidates.</p>
        </div>
        <div>
            <a href="<%= request.getContextPath() %>/projects/upload" class="cta" style="text-decoration:none;display:inline-flex;align-items:center;gap:6px">
                📤 Upload Another Project
            </a>
        </div>
    </div>

    <% if (historyList == null || historyList.isEmpty()) { %>
        <div class="empty-history">
            <div class="icon">📦</div>
            <h3>No Previous Project Analyses</h3>
            <p>You haven't uploaded any Java projects yet in this session. Upload a ZIP, TAR, or TGZ archive to analyze its architecture and track it in history.</p>
            <a href="<%= request.getContextPath() %>/projects/upload" class="cta" style="text-decoration:none">Analyze Java Project</a>
        </div>
    <% } else { %>
        <div style="margin-bottom:14px;font-size:0.85rem;color:#94a3b8">
            Showing <strong><%= historyList.size() %></strong> analyzed project<%= historyList.size() == 1 ? "" : "s" %>:
        </div>

        <% for (HistoryEntry item : historyList) { 
            boolean isActive = activeName != null && activeName.equalsIgnoreCase(item.getProjectName());
        %>
            <div class="history-card <%= isActive ? "active-card" : "" %>">
                <div class="card-top">
                    <div>
                        <div class="proj-title">
                            <span>📦</span> <%= item.getProjectName() %>
                            <% if (isActive) { %>
                                <span class="badge" style="background:rgba(34,197,94,0.2);color:#86efac;border:1px solid #22c55e;font-size:0.75rem;padding:3px 8px">
                                    ● Current Active Project
                                </span>
                            <% } %>
                        </div>
                        <div class="proj-meta">
                            Archive: <code><%= item.getArchiveFileName() %></code> &bull; Analyzed on <%= item.getFormattedDate() %>
                        </div>
                    </div>
                </div>

                <div class="metrics-pill-grid">
                    <div class="metric-item">
                        <span class="lbl">Architecture Health</span>
                        <span class="val health"><%= String.format("%.1f", item.getArchitectureHealth()) %> / 100</span>
                    </div>
                    <div class="metric-item">
                        <span class="lbl">Dependency Pressure</span>
                        <span class="val"><%= String.format("%.1f", item.getDependencyScore()) %> / 100</span>
                    </div>
                    <div class="metric-item">
                        <span class="lbl">Java Files</span>
                        <span class="val"><%= item.getFileCount() %></span>
                    </div>
                    <div class="metric-item">
                        <span class="lbl">Components</span>
                        <span class="val"><%= item.getComponentCount() %></span>
                    </div>
                    <div class="metric-item">
                        <span class="lbl">Cycles</span>
                        <span class="val <%= item.getCycleCount() > 0 ? "cycles" : "" %>"><%= item.getCycleCount() %></span>
                    </div>
                    <div class="metric-item">
                        <span class="lbl">Smells</span>
                        <span class="val <%= item.getSmellCount() > 0 ? "smells" : "" %>"><%= item.getSmellCount() %></span>
                    </div>
                </div>

                <div class="card-actions">
                    <div class="action-btns-left">
                        <a href="<%= request.getContextPath() %>/history?select=<%= item.getId() %>" class="btn-view-analysis">
                            📊 View Dynamic Analysis
                        </a>
                        <a href="<%= request.getContextPath() %>/history?select=<%= item.getId() %>&goto=simulator" class="btn-sim" onclick="sessionStorage.setItem('gotoSim','1');">
                            🔮 What-If Simulator
                        </a>
                    </div>
                    <div>
                        <a href="<%= request.getContextPath() %>/history?delete=<%= item.getId() %>" class="btn-delete" onclick="return confirm('Remove this project from history?');">
                            🗑 Remove from History
                        </a>
                    </div>
                </div>
            </div>
        <% } %>
    <% } %>
</main>

</body>
</html>
