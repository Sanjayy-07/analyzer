<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Upload Java Project — Java Code Analyzer and Refactoring System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar">
    <a href="${pageContext.request.contextPath}/" style="text-decoration:none;color:inherit"><div class="brand">⚡ Java ArchAnalyzer</div></a>
    <nav>
        <a href="${pageContext.request.contextPath}/" style="color:#94a3b8">Home</a>
        <a href="${pageContext.request.contextPath}/dashboard" style="margin-left:20px;color:#94a3b8">Dashboard</a>
        <a href="${pageContext.request.contextPath}/simulator" style="margin-left:20px;color:#a5b4fc;font-weight:600">🔮 What-If Simulator</a>
        <a href="${pageContext.request.contextPath}/history" style="margin-left:20px;background:rgba(56,189,248,0.15);color:#38bdf8;border:1px solid rgba(56,189,248,0.4);padding:6px 14px;border-radius:8px;font-weight:600;text-decoration:none;display:inline-flex;align-items:center;gap:6px">
            🕒 View History
        </a>
    </nav>
</header>

<main class="dashboard">
    <h1>Upload Java Project</h1>
    <p style="color:var(--text-dim);margin-bottom:8px">
        <strong>Java is the only supported programming language.</strong>
        Please upload a Java project archive containing <code>.java</code> source files.
    </p>
    <p style="color:var(--text-dim);font-size:0.9rem;margin-bottom:20px">
        Supported archive formats: ZIP, TAR, TAR.GZ / TGZ.<br>
        Mixed-language projects will have their Java source files analyzed, while non-Java files are preserved intact.<br>
        Non-source resources (build files, XML, JSON, properties, documentation) are supported as project assets.
    </p>

    <% String error = (String) request.getAttribute("errorMessage"); %>
    <% if (error != null) { %>
        <div class="error-banner" style="background:rgba(239,68,68,0.15);border:1px solid #ef4444;color:#fca5a5;padding:12px 16px;border-radius:6px;margin-bottom:20px">
            <strong>Upload Validation Error:</strong> <%= error %>
        </div>
    <% } %>

    <div id="clientError" class="error-banner" style="display:none;background:rgba(239,68,68,0.15);border:1px solid #ef4444;color:#fca5a5;padding:12px 16px;border-radius:6px;margin-bottom:20px"></div>

    <form id="uploadForm" method="post" enctype="multipart/form-data"
          action="${pageContext.request.contextPath}/projects/upload">
        <div class="dropzone" id="dropzone">
            <p><strong>Drag & drop</strong> your Java project archive here (.zip, .tar, .tar.gz), or click to browse.</p>
            <input type="file" name="projectArchive" id="fileInput"
                   accept=".zip,.tar,.gz,.tgz" style="display:none">
            <p id="fileNameLabel" style="color:var(--text);font-weight:600;margin-top:10px"></p>
        </div>
        <button type="submit" id="submitBtn" class="cta" style="margin-top:20px;border:none;cursor:pointer">Analyze Java Project</button>
    </form>

    <div id="loadingIndicator" style="display:none;margin-top:24px;padding:20px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius)">
        <div style="display:flex;align-items:center;gap:16px">
            <div class="spinner"></div>
            <div>
                <strong style="color:var(--text);font-size:1.05rem">Running Full Java Architecture Analysis...</strong>
                <p style="color:var(--text-dim);font-size:0.9rem;margin:4px 0 0 0">
                    Safely extracting archive &bull; Parsing ASTs with Compiler Tree API &bull; Calculating graph metrics &bull;
                    Predicting refactoring risks with Random Forest &bull; Validating refactored Java sources.
                </p>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/upload.js"></script>
</body>
</html>
