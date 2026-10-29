package com.archanalyzer.parser;

import com.archanalyzer.model.ProjectModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Modular Language Adapter abstraction.
 * Represents a language-specific parsing, dependency-extraction, and analysis plugin.
 * Every supported language (Java, JavaScript, TypeScript, Python) provides a LanguageAdapter
 * that contributes to the unified, language-neutral ProjectModel.
 */
public interface LanguageAdapter extends ParserAdapter {

    /** Set of supported file extensions handled by this adapter, e.g. {".js", ".jsx", ".mjs", ".cjs"}. */
    Set<String> supportedExtensions();

    /** Declares the precise capability level of this adapter. */
    LanguageCapability getCapabilities();

    /** Whether this adapter supports automated code modification / refactoring. */
    default boolean canModify() {
        return getCapabilities().isSafeModification();
    }

    /**
     * Checks syntax validity of a file.
     * @return null if valid, or a descriptive error message if invalid.
     */
    default String validateSyntax(Path file) {
        return null;
    }
}
