package com.archanalyzer.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageDetectorTest {

    private LanguageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new LanguageDetector();
    }

    @Test
    void testJavaSourceSupported() {
        Path javaFile = Path.of("src/com/example/UserService.java");
        LanguageDetector.DetectionResult result = detector.detect(javaFile);

        assertTrue(result.isSupported());
        assertTrue(result.isJava());
        assertFalse(result.isUnsupportedLanguage());
        assertFalse(result.isResource());
        assertEquals("java", result.getLanguage());
        assertTrue(detector.isJavaSource(javaFile));
        assertFalse(detector.isUnsupportedLanguage(javaFile));
        assertEquals("Full", result.getCapability().getDepthSummary());
    }

    @Test
    void testSupportedLanguagesJavaScriptTypeScriptPython() {
        String[] supportedFiles = {
            "index.js", "component.jsx", "bundle.mjs", "server.cjs",
            "types.ts", "view.tsx",
            "app.py", "script.pyw"
        };

        for (String file : supportedFiles) {
            Path p = Path.of(file);
            LanguageDetector.DetectionResult r = detector.detect(p);
            assertTrue(r.isSupported(), "Should be supported: " + file);
            assertFalse(r.isJava(), "Should not be Java: " + file);
            assertFalse(r.isUnsupportedLanguage(), "Should not be categorized as unsupported: " + file);
            assertFalse(r.isResource(), "Should not be plain resource: " + file);
            assertNotNull(r.getCapability(), "Capability must be defined for: " + file);
            assertEquals("Standard Analysis", r.getCapability().getDepthSummary());
        }
    }

    @Test
    void testUnsupportedProgrammingLanguages() {
        String[] unsupported = {
            "module.pyx", "Program.cs", "main.go", "index.php",
            "main.c", "engine.cpp", "header.h", "header.hpp",
            "script.rb", "lib.rs", "App.swift", "Main.kt", "Build.scala"
        };

        for (String file : unsupported) {
            Path p = Path.of(file);
            LanguageDetector.DetectionResult r = detector.detect(p);
            assertFalse(r.isSupported(), "Should not be supported: " + file);
            assertFalse(r.isJava(), "Should not be Java: " + file);
            assertTrue(r.isUnsupportedLanguage(), "Should be recognized as unsupported language: " + file);
            assertFalse(r.isResource(), "Should not be categorized as plain resource: " + file);
            assertFalse(detector.isJavaSource(p));
            assertTrue(detector.isUnsupportedLanguage(p));
        }
    }

    @Test
    void testProjectResourcesNotMisclassified() {
        String[] resources = {
            "pom.xml", "build.gradle", "application.properties",
            "config.json", "schema.sql", "README.md",
            "data.yaml", "settings.toml", "index.html",
            "style.css", "icon.png", "Dockerfile", ".gitignore"
        };

        for (String file : resources) {
            Path p = Path.of(file);
            LanguageDetector.DetectionResult r = detector.detect(p);
            assertFalse(r.isSupported(), "Resource should not be supported source: " + file);
            assertFalse(r.isJava(), "Resource must not be Java: " + file);
            assertFalse(r.isUnsupportedLanguage(), "Resource must not be treated as unsupported language: " + file);
            assertTrue(r.isResource(), "Must be classified as resource: " + file);
        }
    }

    @Test
    void testAnalysisDepthNeverReturnsPartial() {
        assertEquals("Full", detector.analysisDepthFor("java"));
        assertEquals("Full", detector.analysisDepthFor("JAVA"));
        assertEquals("Standard Analysis", detector.analysisDepthFor("javascript"));
        assertEquals("Standard Analysis", detector.analysisDepthFor("typescript"));
        assertEquals("Standard Analysis", detector.analysisDepthFor("python"));
        assertEquals("Unsupported", detector.analysisDepthFor("ruby"));
        assertEquals("Unsupported", detector.analysisDepthFor("unknown"));
    }
}
