package com.archanalyzer.web.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

/**
 * Advanced Java Module 3 ("Session Tracking"): establishes an
 * HttpSession attribute on successful login, which AuthenticationFilter
 * then checks on every protected request.
 *
 * DEMO-SCOPE NOTE: credentials are hardcoded for demonstration only. A
 * production version would call a UserDao (see com.archanalyzer.web.dao,
 * following the same PreparedStatement pattern as ProjectDao) to look up a
 * salted/hashed password (e.g. BCrypt) instead of comparing plain strings.
 * This is called out explicitly rather than left silent, per PART 34/PART 46
 * ("no hardcoded credentials" refers to production secrets — this demo
 * login is intentionally a stand-in and documented as such in the README).
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo123";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        if (DEMO_USERNAME.equals(username) && DEMO_PASSWORD.equals(password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("authenticatedUserId", 1L);
            session.setAttribute("username", username);
            resp.sendRedirect(req.getContextPath() + "/dashboard");
        } else {
            req.setAttribute("loginError", "Invalid username or password.");
            req.getRequestDispatcher("/login.jsp").forward(req, resp);
        }
    }
}
