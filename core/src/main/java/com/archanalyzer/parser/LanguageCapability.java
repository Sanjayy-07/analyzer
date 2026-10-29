package com.archanalyzer.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares the precise capability level for each programming language.
 * Enforces the rule: Never claim a language is fully supported merely because its
 * files are detected or preserved in a ZIP.
 */
public class LanguageCapability {

    private final String language;
    private final boolean fileDetection;
    private final boolean syntaxParsing;
    private final boolean dependencyExtraction;
    private final boolean architectureAnalysis;
    private final boolean changeImpactAnalysis;
    private final boolean safeModification;
    private final boolean validation;
    private final String statusNotes;

    public LanguageCapability(String language,
                              boolean fileDetection,
                              boolean syntaxParsing,
                              boolean dependencyExtraction,
                              boolean architectureAnalysis,
                              boolean changeImpactAnalysis,
                              boolean safeModification,
                              boolean validation,
                              String statusNotes) {
        this.language = language;
        this.fileDetection = fileDetection;
        this.syntaxParsing = syntaxParsing;
        this.dependencyExtraction = dependencyExtraction;
        this.architectureAnalysis = architectureAnalysis;
        this.changeImpactAnalysis = changeImpactAnalysis;
        this.safeModification = safeModification;
        this.validation = validation;
        this.statusNotes = statusNotes;
    }

    public static LanguageCapability java() {
        return new LanguageCapability(
            "java",
            true,  // fileDetection
            true,  // syntaxParsing (Compiler Tree API)
            true,  // dependencyExtraction
            true,  // architectureAnalysis
            true,  // changeImpactAnalysis
            true,  // safeModification (Interface extraction, feature addition)
            true,  // validation (Javac / Maven / tests)
            "Full support via JDK Compiler Tree API and BuildValidator."
        );
    }

    public static LanguageCapability javaScript() {
        return new LanguageCapability(
            "javascript",
            true,  // fileDetection
            true,  // syntaxParsing (JS syntax & AST scanner)
            true,  // dependencyExtraction (ES6 import/export, CommonJS require)
            true,  // architectureAnalysis (classes, functions, Express routes, React components)
            true,  // changeImpactAnalysis
            true,  // safeModification (feature addition, function updates)
            false, // validation (requires external runtime/npm)
            "Supported for detection, AST parsing, dependencies, metrics, and change impact."
        );
    }

    public static LanguageCapability typeScript() {
        return new LanguageCapability(
            "typescript",
            true,  // fileDetection
            true,  // syntaxParsing (TS syntax & AST scanner)
            true,  // dependencyExtraction (types, interfaces, imports, exports)
            true,  // architectureAnalysis (classes, interfaces, types, components)
            true,  // changeImpactAnalysis
            false, // safeModification (preview only)
            false, // validation (tsc required)
            "Supported for detection, syntax parsing, interfaces, dependencies, and impact analysis."
        );
    }

    public static LanguageCapability python() {
        return new LanguageCapability(
            "python",
            true,  // fileDetection
            true,  // syntaxParsing (Python syntax scanner)
            true,  // dependencyExtraction (import, from-import)
            true,  // architectureAnalysis (classes, functions, complexity)
            true,  // changeImpactAnalysis
            false, // safeModification (preview only)
            false, // validation
            "Supported for detection, AST parsing, functions, classes, and dependency extraction."
        );
    }

    public static LanguageCapability unsupported(String language) {
        return new LanguageCapability(
            language,
            true,  // fileDetection
            false, // syntaxParsing
            false, // dependencyExtraction
            false, // architectureAnalysis
            false, // changeImpactAnalysis
            false, // safeModification
            false, // validation
            "Detected in archive and preserved in project deliverable; source analysis unsupported."
        );
    }

    public String getLanguage() { return language; }
    public boolean isFileDetection() { return fileDetection; }
    public boolean isSyntaxParsing() { return syntaxParsing; }
    public boolean isDependencyExtraction() { return dependencyExtraction; }
    public boolean isArchitectureAnalysis() { return architectureAnalysis; }
    public boolean isChangeImpactAnalysis() { return changeImpactAnalysis; }
    public boolean isSafeModification() { return safeModification; }
    public boolean isValidation() { return validation; }
    public String getStatusNotes() { return statusNotes; }

    public String getDepthSummary() {
        if (syntaxParsing && dependencyExtraction && safeModification && validation) {
            return "Full";
        }
        if (syntaxParsing && dependencyExtraction && architectureAnalysis) {
            return "Standard Analysis";
        }
        if (fileDetection) {
            return "Preserved / Unsupported";
        }
        return "Unknown";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("language", language);
        map.put("fileDetection", fileDetection);
        map.put("syntaxParsing", syntaxParsing);
        map.put("dependencyExtraction", dependencyExtraction);
        map.put("architectureAnalysis", architectureAnalysis);
        map.put("changeImpactAnalysis", changeImpactAnalysis);
        map.put("safeModification", safeModification);
        map.put("validation", validation);
        map.put("depthSummary", getDepthSummary());
        map.put("notes", statusNotes);
        return map;
    }
}
