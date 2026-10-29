package com.archanalyzer.web.servlet;

import com.archanalyzer.optimization.ConstraintSet;
import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.web.config.AppContextListener;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Advanced Java Module 3 (Servlets: "Reading and Sending data").
 * ---------------------------------------------------------------------------
 * PART 23 (Upload Project page) / PART 44 (Faculty Demonstration Mode step
 * 2): accepts a multipart archive upload, runs the REAL AnalyzerPipeline
 * (safe extraction -> parsing -> graph -> metrics -> smells -> ML risk ->
 * candidates -> ranking), stores the result in the session, and forwards to
 * the results JSP. No fake progress bars or hardcoded results (PART 34) —
 * whatever the JSP renders comes directly from the PipelineResult object.
 */
@WebServlet(name = "UploadServlet", urlPatterns = "/projects/upload")
@MultipartConfig
public class UploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Part filePart = req.getPart("projectArchive");
        if (filePart == null || filePart.getSize() == 0) {
            req.setAttribute("errorMessage", "No file was uploaded. Please choose a project archive.");
            req.getRequestDispatcher("/WEB-INF/jsp/upload.jsp").forward(req, resp);
            return;
        }

        String submittedFileName = filePart.getSubmittedFileName();
        Path tempDir = Files.createTempDirectory("archanalyzer-upload-");
        Path archivePath = tempDir.resolve(sanitizeFileName(submittedFileName));

        try {
            try (InputStream in = filePart.getInputStream()) {
                Files.copy(in, archivePath, StandardCopyOption.REPLACE_EXISTING);
            }

            AnalyzerPipeline pipeline = (AnalyzerPipeline) getServletContext().getAttribute(AppContextListener.PIPELINE_ATTR);
            if (pipeline == null) {
                throw new ServletException("AnalyzerPipeline was not initialized at application startup.");
            }

            String projectName = stripExtension(submittedFileName);

            AnalyzerPipeline.PipelineResult result = pipeline.run(
                archivePath, tempDir.resolve("work"), projectName, ConstraintSet.defaults());

            HttpSession session = req.getSession();
            Path oldTempDir = (Path) session.getAttribute("activeUploadTempDir");
            if (oldTempDir != null && !oldTempDir.equals(tempDir)) {
                deleteRecursiveSilently(oldTempDir);
            }
            session.setAttribute("activeUploadTempDir", tempDir);
            session.setAttribute("lastPipelineResult", result);

            resp.sendRedirect(req.getContextPath() + "/dashboard");
        } catch (IllegalArgumentException | UnsupportedOperationException | com.archanalyzer.archive.ArchiveSecurityException e) {
            deleteRecursiveSilently(tempDir);
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/jsp/upload.jsp").forward(req, resp);
        } catch (Exception e) {
            deleteRecursiveSilently(tempDir);
            getServletContext().log("Analysis pipeline failed", e);
            String userMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                ? "Analysis failed: " + e.getMessage()
                : "Analysis failed. Please check the archive and try again.";
            req.setAttribute("errorMessage", userMsg);
            req.getRequestDispatcher("/WEB-INF/jsp/upload.jsp").forward(req, resp);
        }
    }

    private void deleteRecursiveSilently(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            var list = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : list) {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/jsp/upload.jsp").forward(req, resp);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "upload.zip";
        String baseName = Paths.get(name).getFileName().toString();
        return baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String stripExtension(String name) {
        if (name == null || name.isBlank()) return "project";
        String baseName = Paths.get(name).getFileName().toString();
        String lower = baseName.toLowerCase();
        if (lower.endsWith(".tar.gz")) return baseName.substring(0, baseName.length() - 7);
        if (lower.endsWith(".tar.bz2")) return baseName.substring(0, baseName.length() - 8);
        int dot = baseName.lastIndexOf('.');
        return dot > 0 ? baseName.substring(0, dot) : baseName;
    }
}
