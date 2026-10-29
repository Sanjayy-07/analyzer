<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Refactoring Constraints</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar"><div class="brand">ArchAnalyzer</div></header>
<main class="dashboard" style="max-width:520px">
    <h1>Refactoring Constraints</h1>
    <p style="color:var(--text-dim)">These limits are enforced by the optimization engine when ranking
        candidate architectures (PART 16/17) — a candidate that violates them is excluded before scoring,
        not just deprioritized.</p>

    <form:form modelAttribute="constraintForm" method="post" action="${pageContext.request.contextPath}/mvc/constraints">
        <p>
            <label>Maximum files changed<br>
                <form:input path="maxFilesChanged" type="number" style="width:100%;padding:8px"/>
            </label>
            <form:errors path="maxFilesChanged" cssClass="error-banner" element="div"/>
        </p>
        <p>
            <label><form:checkbox path="noNewExternalDependencies"/> No new external dependencies</label>
        </p>
        <p>
            <label><form:checkbox path="preservePublicApi"/> Preserve public API</label>
        </p>
        <p>
            <label><form:checkbox path="preferLowRisk"/> Prefer low ML-predicted risk</label>
        </p>
        <button type="submit" class="cta" style="border:none;cursor:pointer">Save Constraints</button>
    </form:form>
</main>
</body>
</html>
