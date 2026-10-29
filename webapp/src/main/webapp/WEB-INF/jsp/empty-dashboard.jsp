<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar">
    <div class="brand">ArchAnalyzer</div>
    <nav><a href="${pageContext.request.contextPath}/projects/upload">Upload</a></nav>
</header>
<main class="dashboard">
    <div class="empty-state">
        <h2>No analysis yet</h2>
        <p>Upload a project archive to see its dependency graph, architecture health, and ML risk predictions.</p>
        <a class="cta" href="${pageContext.request.contextPath}/projects/upload">Upload a project</a>
    </div>
</main>
</body>
</html>
