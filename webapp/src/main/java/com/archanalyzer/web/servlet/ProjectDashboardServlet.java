package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

/**
 * PART 23 (Dashboard / Project Overview / Architecture Dashboard / Risk
 * Analysis screens, condensed into one servlet + JSP for this build).
 * Reads the AnalyzerPipeline.PipelineResult the UploadServlet stored in the
 * session and exposes it as request attributes for results.jsp to render
 * with JSP scriptlets/expression tags (Module 4).
 */
@WebServlet(name = "ProjectDashboardServlet", urlPatterns = "/dashboard")
public class ProjectDashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult result = session != null
            ? (AnalyzerPipeline.PipelineResult) session.getAttribute("lastPipelineResult") : null;

        if (result == null) {
            req.getRequestDispatcher("/WEB-INF/jsp/empty-dashboard.jsp").forward(req, resp);
            return;
        }

        req.setAttribute("result", result);
        req.getRequestDispatcher("/WEB-INF/jsp/results.jsp").forward(req, resp);
    }
}
