package com.archanalyzer.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Advanced Java Module 3 ("filters/listeners", "session tracking"): a
 * straightforward session-based gate. Real password verification lives in
 * AuthServlet/UserDao (Module 1: JDBC + PreparedStatement) — this filter
 * only checks that a session with an authenticated user already exists.
 */
@WebFilter(filterName = "AuthenticationFilter")
public class AuthenticationFilter implements Filter {

    private static final String SESSION_USER_ATTR = "authenticatedUserId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        boolean authenticated = session != null && session.getAttribute(SESSION_USER_ATTR) != null;
        if (!authenticated) {
            res.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        chain.doFilter(request, response);
    }
}
