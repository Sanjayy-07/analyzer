<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.archanalyzer.optimization.ConstraintSet" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Constraints Saved</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar"><div class="brand">ArchAnalyzer</div></header>
<main class="dashboard" style="max-width:520px">
    <h1>Constraints Saved</h1>
    <%
        ConstraintSet cs = (ConstraintSet) request.getAttribute("savedConstraints");
    %>
    <ul>
        <li>Maximum files changed: <%= cs.maxFilesChanged %></li>
        <li>No new external dependencies: <%= cs.noNewExternalDependencies %></li>
        <li>Preserve public API: <%= cs.preservePublicApi %></li>
        <li>Prefer low risk: <%= cs.preferLowRisk %></li>
    </ul>
    <a class="cta" href="${pageContext.request.contextPath}/dashboard">Back to Dashboard</a>
</main>
</body>
</html>
