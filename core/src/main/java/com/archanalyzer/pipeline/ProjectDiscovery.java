package com.archanalyzer.pipeline;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.*;
import com.archanalyzer.parser.LanguageDetector.DetectionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Discovers and parses source files across supported languages (Java, JavaScript, TypeScript, Python)
 * into the common ProjectModel using modular LanguageAdapters.
 *
 * Preserves non-source configuration, documentation, web, and build files.
 * Reports unsupported languages honestly without silently dropping files.
 */
public class ProjectDiscovery {

    public static final String NO_JAVA_FILES_MESSAGE =
        "No Java source files were found. Please upload a Java project containing .java files.";

    public static final String NO_SUPPORTED_FILES_MESSAGE =
        "No supported source files (Java, JavaScript, TypeScript, Python) were found in the project.";

    /** Common build, version control, IDE, and dependency cache directories to skip during source discovery. */
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
        ".git", ".svn", ".hg", "node_modules", "target", "build", "dist",
        "out", "bin", ".idea", ".vscode", ".settings", "__pycache__", ".gradle"
    );

    private final LanguageDetector detector = new LanguageDetector();
    private final Map<String, LanguageAdapter> adaptersByExtension = new LinkedHashMap<>();
    private final List<LanguageAdapter> adapters = new ArrayList<>();
    private final boolean javaOnlyMode;

    public ProjectDiscovery() {
        this(false);
    }

    public ProjectDiscovery(boolean javaOnlyMode) {
        this.javaOnlyMode = javaOnlyMode;
        registerAdapter(new JavaParserAdapter());
        if (!javaOnlyMode) {
            registerAdapter(new JavaScriptLanguageAdapter());
            registerAdapter(new TypeScriptLanguageAdapter());
            registerAdapter(new PythonLanguageAdapter());
        }
    }

    public void registerAdapter(LanguageAdapter adapter) {
        adapters.add(adapter);
        for (String ext : adapter.supportedExtensions()) {
            adaptersByExtension.put(ext.toLowerCase(), adapter);
        }
    }

    public ProjectModel discoverAndParse(Path projectRoot, String projectName) throws IOException {
        if (projectRoot == null || !Files.exists(projectRoot)) {
            throw new IllegalArgumentException("Project directory does not exist: " + projectRoot);
        }

        ProjectModel model = new ProjectModel(projectName);
        detectBuildTools(projectRoot, model);

        // Record capabilities of all active adapters
        for (LanguageAdapter a : adapters) {
            model.recordLanguageCapability(a.getCapabilities());
        }

        List<Path> allFiles;
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            allFiles = walk
                .filter(Files::isRegularFile)
                .filter(p -> !isSkippedPath(projectRoot, p))
                .toList();
        }

        List<Path> sourceFilesToParse = new ArrayList<>();
        int javaCount = 0;

        for (Path file : allFiles) {
            DetectionResult result = detector.detect(file);
            String relative = projectRoot.relativize(file).toString().replace('\\', '/');
            String ext = result.getExtension().toLowerCase();

            if (javaOnlyMode) {
                if (result.isJava()) {
                    sourceFilesToParse.add(file);
                    javaCount++;
                } else if (!result.isResource()) {
                    model.addExcludedNonJavaFile(relative);
                    model.addExcludedLanguage(result.getLanguage());
                    model.recordLanguageCapability(LanguageCapability.unsupported(result.getLanguage()));
                }
            } else {
                LanguageAdapter adapter = adaptersByExtension.get(ext);
                if (adapter != null) {
                    sourceFilesToParse.add(file);
                    if (result.isJava()) javaCount++;
                } else if (result.isUnsupportedLanguage()) {
                    model.addExcludedNonJavaFile(relative);
                    model.addExcludedLanguage(result.getLanguage());
                    model.recordLanguageCapability(LanguageCapability.unsupported(result.getLanguage()));
                }
            }
        }

        if (javaOnlyMode) {
            if (javaCount == 0) {
                throw new IllegalArgumentException(NO_JAVA_FILES_MESSAGE);
            }
        } else {
            if (sourceFilesToParse.isEmpty()) {
                throw new IllegalArgumentException(javaCount == 0 && !hasUnsupportedSource(model)
                    ? NO_JAVA_FILES_MESSAGE
                    : NO_SUPPORTED_FILES_MESSAGE);
            }
        }

        for (Path file : sourceFilesToParse) {
            String relative = projectRoot.relativize(file).toString().replace('\\', '/');
            String fileName = file.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String ext = dot >= 0 ? fileName.substring(dot).toLowerCase() : "";

            LanguageAdapter adapter = adaptersByExtension.get(ext);
            if (adapter != null) {
                try {
                    adapter.parseInto(projectRoot, file, model);
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    model.addParseError(relative, errorMsg);
                    System.err.println("WARNING: Failed to parse " + adapter.language() + " file " + relative + ": " + errorMsg);
                }
            }
        }

        return model;
    }

    private boolean hasUnsupportedSource(ProjectModel model) {
        return !model.getExcludedLanguages().isEmpty();
    }

    public boolean isSkippedPath(Path projectRoot, Path path) {
        Path relative = projectRoot.relativize(path);
        for (Path part : relative) {
            if (SKIP_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private void detectBuildTools(Path projectRoot, ProjectModel model) {
        // Maven
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            model.putBuildInfo("buildTool", "maven");
        } else if (Files.exists(projectRoot.resolve("build.gradle")) || Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            model.putBuildInfo("buildTool", "gradle");
        } else if (Files.exists(projectRoot.resolve("package.json"))) {
            model.putBuildInfo("buildTool", "npm");
        } else if (Files.exists(projectRoot.resolve("requirements.txt")) || Files.exists(projectRoot.resolve("pyproject.toml"))) {
            model.putBuildInfo("buildTool", "pip/poetry");
        } else {
            // Check immediate child directories
            try (Stream<Path> list = Files.list(projectRoot)) {
                for (Path child : list.filter(Files::isDirectory).toList()) {
                    if (Files.exists(child.resolve("pom.xml"))) {
                        model.putBuildInfo("buildTool", "maven");
                        return;
                    }
                    if (Files.exists(child.resolve("build.gradle")) || Files.exists(child.resolve("build.gradle.kts"))) {
                        model.putBuildInfo("buildTool", "gradle");
                        return;
                    }
                    if (Files.exists(child.resolve("package.json"))) {
                        model.putBuildInfo("buildTool", "npm");
                        return;
                    }
                }
            } catch (IOException ignored) {}
            model.putBuildInfo("buildTool", "unknown");
        }
    }

    public LanguageDetector getDetector() {
        return detector;
    }

    public boolean isJavaOnlyMode() {
        return javaOnlyMode;
    }
}
