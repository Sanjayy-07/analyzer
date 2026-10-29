package com.archanalyzer.parser;

import java.nio.file.Path;
import java.util.*;

/**
 * Detects programming languages and provides capability-aware classification
 * across Java, JavaScript, TypeScript, Python, and other languages.
 *
 * Distinguishes supported languages from unsupported programming languages and
 * non-source resources (configs, documentation, build files, web assets).
 */
public final class LanguageDetector {

    public static final String JAVA_LANGUAGE = "java";
    public static final String JAVASCRIPT_LANGUAGE = "javascript";
    public static final String TYPESCRIPT_LANGUAGE = "typescript";
    public static final String PYTHON_LANGUAGE = "python";

    public static final String JAVA_EXTENSION = ".java";

    /** Fully supported primary language. */
    public static final Set<String> PRIMARY_LANGUAGES = Set.of(JAVA_LANGUAGE);

    /** Supported source languages that have LanguageAdapters. */
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of(
        JAVA_LANGUAGE, JAVASCRIPT_LANGUAGE, TYPESCRIPT_LANGUAGE, PYTHON_LANGUAGE
    );

    /** Language to extension mapping for supported languages. */
    private static final Map<String, String> SUPPORTED_EXTENSIONS = Map.ofEntries(
        Map.entry(".java", JAVA_LANGUAGE),
        Map.entry(".js", JAVASCRIPT_LANGUAGE),
        Map.entry(".jsx", JAVASCRIPT_LANGUAGE),
        Map.entry(".mjs", JAVASCRIPT_LANGUAGE),
        Map.entry(".cjs", JAVASCRIPT_LANGUAGE),
        Map.entry(".ts", TYPESCRIPT_LANGUAGE),
        Map.entry(".tsx", TYPESCRIPT_LANGUAGE),
        Map.entry(".py", PYTHON_LANGUAGE),
        Map.entry(".pyw", PYTHON_LANGUAGE)
    );

    /** Known unsupported programming language extensions. */
    private static final Map<String, String> UNSUPPORTED_LANGUAGES = Map.ofEntries(
        Map.entry(".pyx", "python-extension"),
        Map.entry(".cs", "csharp"),
        Map.entry(".go", "go"),
        Map.entry(".php", "php"),
        Map.entry(".c", "c"),
        Map.entry(".cpp", "cpp"),
        Map.entry(".cc", "cpp"),
        Map.entry(".cxx", "cpp"),
        Map.entry(".h", "c/cpp-header"),
        Map.entry(".hpp", "cpp-header"),
        Map.entry(".hh", "cpp-header"),
        Map.entry(".rb", "ruby"),
        Map.entry(".rs", "rust"),
        Map.entry(".swift", "swift"),
        Map.entry(".kt", "kotlin"),
        Map.entry(".kts", "kotlin"),
        Map.entry(".scala", "scala"),
        Map.entry(".dart", "dart"),
        Map.entry(".lua", "lua"),
        Map.entry(".r", "r"),
        Map.entry(".pl", "perl"),
        Map.entry(".pm", "perl"),
        Map.entry(".groovy", "groovy")
    );

    /** Known non-source configuration, documentation, web, and build resource extensions. */
    private static final Set<String> RESOURCE_EXTENSIONS = Set.of(
        ".xml", ".json", ".yaml", ".yml", ".properties", ".toml", ".ini", ".conf", ".cfg", ".env",
        ".md", ".markdown", ".txt", ".adoc", ".rst", ".pdf",
        ".html", ".htm", ".jsp", ".jspx", ".css", ".scss", ".sass", ".less",
        ".sql", ".csv", ".tsv",
        ".gradle", ".pom", ".classpath", ".project",
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".jar", ".war", ".class",
        ".sh", ".bat", ".cmd", ".ps1"
    );

    /**
     * Structured result representing the language classification of a file.
     */
    public static final class DetectionResult {
        private final String language;
        private final boolean supported;
        private final boolean unsupportedLanguage;
        private final boolean resource;
        private final String extension;
        private final LanguageCapability capability;

        private DetectionResult(String language, boolean supported, boolean unsupportedLanguage,
                                boolean resource, String extension, LanguageCapability capability) {
            this.language = language;
            this.supported = supported;
            this.unsupportedLanguage = unsupportedLanguage;
            this.resource = resource;
            this.extension = extension;
            this.capability = capability;
        }

        public static DetectionResult java(String extension) {
            return new DetectionResult(JAVA_LANGUAGE, true, false, false, extension, LanguageCapability.java());
        }

        public static DetectionResult supportedLang(String language, String extension) {
            LanguageCapability cap = switch (language) {
                case JAVASCRIPT_LANGUAGE -> LanguageCapability.javaScript();
                case TYPESCRIPT_LANGUAGE -> LanguageCapability.typeScript();
                case PYTHON_LANGUAGE -> LanguageCapability.python();
                default -> LanguageCapability.unsupported(language);
            };
            return new DetectionResult(language, true, false, false, extension, cap);
        }

        public static DetectionResult unsupported(String language, String extension) {
            return new DetectionResult(language, false, true, false, extension, LanguageCapability.unsupported(language));
        }

        public static DetectionResult resource(String extension) {
            return new DetectionResult("resource", false, false, true, extension, null);
        }

        public static DetectionResult unknown(String extension) {
            return new DetectionResult("unknown", false, false, false, extension, null);
        }

        public String getLanguage() { return language; }
        public boolean isSupported() { return supported; }
        public boolean isJava() { return JAVA_LANGUAGE.equalsIgnoreCase(language); }
        public boolean isJavaScript() { return JAVASCRIPT_LANGUAGE.equalsIgnoreCase(language); }
        public boolean isTypeScript() { return TYPESCRIPT_LANGUAGE.equalsIgnoreCase(language); }
        public boolean isPython() { return PYTHON_LANGUAGE.equalsIgnoreCase(language); }
        public boolean isUnsupportedLanguage() { return unsupportedLanguage; }
        public boolean isResource() { return resource; }
        public String getExtension() { return extension; }
        public LanguageCapability getCapability() { return capability; }

        @Override
        public String toString() {
            return "DetectionResult[language=" + language + ", supported=" + supported +
                   ", unsupportedLanguage=" + unsupportedLanguage + ", resource=" + resource + "]";
        }
    }

    /**
     * Classifies a file path into supported source, unsupported programming language, or resource/unknown.
     */
    public DetectionResult detect(Path file) {
        if (file == null) {
            return DetectionResult.unknown("");
        }
        String fileName = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return DetectionResult.resource(dot == 0 ? fileName : "");
        }
        String ext = fileName.substring(dot);

        if (JAVA_EXTENSION.equals(ext)) {
            return DetectionResult.java(ext);
        }

        String supportedLang = SUPPORTED_EXTENSIONS.get(ext);
        if (supportedLang != null) {
            return DetectionResult.supportedLang(supportedLang, ext);
        }

        String unsupportedLang = UNSUPPORTED_LANGUAGES.get(ext);
        if (unsupportedLang != null) {
            return DetectionResult.unsupported(unsupportedLang, ext);
        }

        if (RESOURCE_EXTENSIONS.contains(ext)) {
            return DetectionResult.resource(ext);
        }

        return DetectionResult.unknown(ext);
    }

    public boolean isJavaSource(Path file) {
        return detect(file).isJava();
    }

    public boolean isSupportedSource(Path file) {
        return detect(file).isSupported();
    }

    public boolean isUnsupportedLanguage(Path file) {
        return detect(file).isUnsupportedLanguage();
    }

    public String analysisDepthFor(String language) {
        if (language == null) return "Unsupported";
        return switch (language.toLowerCase()) {
            case "java" -> LanguageCapability.java().getDepthSummary();
            case "javascript" -> LanguageCapability.javaScript().getDepthSummary();
            case "typescript" -> LanguageCapability.typeScript().getDepthSummary();
            case "python" -> LanguageCapability.python().getDepthSummary();
            default -> "Unsupported";
        };
    }

    public LanguageCapability capabilityFor(String language) {
        if (language == null) return LanguageCapability.unsupported("unknown");
        return switch (language.toLowerCase()) {
            case "java" -> LanguageCapability.java();
            case "javascript" -> LanguageCapability.javaScript();
            case "typescript" -> LanguageCapability.typeScript();
            case "python" -> LanguageCapability.python();
            default -> LanguageCapability.unsupported(language);
        };
    }
}
