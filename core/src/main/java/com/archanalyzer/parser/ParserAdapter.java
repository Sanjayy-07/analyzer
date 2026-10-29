package com.archanalyzer.parser;

import com.archanalyzer.model.ProjectModel;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Parser adapter abstraction for source language parsing.
 * In this system, Java is the only supported source language.
 */
public interface ParserAdapter {

    /** Language identifier this adapter handles, e.g. "java". */
    String language();

    /** Analysis depth: "Full" for Java. Unsupported languages are not parsed. */
    String analysisDepth();

    /** @return true if this adapter should process the given file (by extension/content). */
    boolean canParse(Path file);

    /**
     * Parses a single source file and registers its components/dependencies
     * into the shared ProjectModel.
     */
    void parseInto(Path projectRoot, Path file, ProjectModel model) throws IOException;
}
