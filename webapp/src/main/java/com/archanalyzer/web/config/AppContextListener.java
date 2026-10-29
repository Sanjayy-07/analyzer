package com.archanalyzer.web.config;

import com.archanalyzer.ml.RiskPredictionService;
import com.archanalyzer.pipeline.AnalyzerPipeline;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Advanced Java Module 2 (J2EE Basics: "J2EE Application Life Cycle").
 * Loads the (relatively expensive to construct — it reads and parses the
 * trained Random Forest's ~700KB text model) RiskPredictionService and a
 * shared AnalyzerPipeline ONCE at application startup, rather than
 * per-request, and publishes them via ServletContext attributes for the
 * Servlets/Spring beans to reuse.
 *
 * Also implements HttpSessionListener to cleanly delete temporary upload
 * work directories when user sessions expire.
 */
@WebListener
public class AppContextListener implements ServletContextListener, HttpSessionListener {

    public static final String PIPELINE_ATTR = "archanalyzer.pipeline";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            AnalyzerPipeline pipeline = new AnalyzerPipeline();
            sce.getServletContext().setAttribute(PIPELINE_ATTR, pipeline);
            sce.getServletContext().log("AnalyzerPipeline initialized at startup.");
        } catch (Exception e) {
            sce.getServletContext().log("Failed to initialize AnalyzerPipeline", e);
            throw new IllegalStateException("Application startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        sce.getServletContext().log("Application shutting down.");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        Path tempDir = (Path) se.getSession().getAttribute("activeUploadTempDir");
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                var list = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path p : list) {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }
}
