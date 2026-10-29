package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.file.Files;

@WebServlet(name = "DownloadServlet", urlPatterns = "/projects/download/*")
public class DownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult result = session != null
            ? (AnalyzerPipeline.PipelineResult) session.getAttribute("lastPipelineResult") : null;

        String what = req.getPathInfo(); // "/zip" or "/report"

        if (result == null || what == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (what.equals("/zip")) {
            if (result.finalZipPath == null || !Files.exists(result.finalZipPath)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No project ZIP archive is available for this analysis.");
                return;
            }
            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + result.finalZipPath.getFileName() + "\"");
            Files.copy(result.finalZipPath, resp.getOutputStream());
        } else if (what.equals("/report")) {
            resp.setContentType("text/markdown;charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"report.md\"");
            resp.getWriter().write(result.reportMarkdown);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
