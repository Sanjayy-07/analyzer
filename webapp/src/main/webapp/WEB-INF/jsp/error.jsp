<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Something went wrong</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar"><div class="brand">ArchAnalyzer</div></header>
<main class="dashboard">
    <div class="empty-state">
        <h2>Something went wrong</h2>
        <p>
            An unexpected error occurred while processing your request.
            <%-- PART 31: never show a raw stack trace to normal users; the
                 `exception` implicit object (available because isErrorPage=true)
                 is logged server-side only. --%>
        </p>
        <%
            if (exception != null) {
                application.log("Unhandled exception reached error.jsp", exception);
            }
        %>
        <a class="cta" href="${pageContext.request.contextPath}/">Back to home</a>
    </div>
</main>
</body>
</html>
