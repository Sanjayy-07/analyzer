<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Log In — ArchAnalyzer</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<main class="dashboard" style="max-width:420px">
    <h1>Log In</h1>
    <% String error = (String) request.getAttribute("loginError"); %>
    <% if (error != null) { %><div class="error-banner"><%= error %></div><% } %>
    <form method="post" action="${pageContext.request.contextPath}/login">
        <p><label>Username<br>
            <input type="text" name="username" style="width:100%;padding:8px" required></label></p>
        <p><label>Password<br>
            <input type="password" name="password" style="width:100%;padding:8px" required></label></p>
        <button type="submit" class="cta" style="border:none;cursor:pointer">Log In</button>
    </form>
    <p style="color:var(--text-dim);font-size:0.85rem">
        Demo credentials: <code>demo</code> / <code>demo123</code>
        (see LoginServlet — replace with real UserDao + password hashing before production use).
    </p>
</main>
</body>
</html>
