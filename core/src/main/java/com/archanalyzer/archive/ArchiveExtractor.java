package com.archanalyzer.archive;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ADAPTER PATTERN: one adapter per archive format.
 * PART 8 of the spec: extraction must be safe (path traversal, zip-bomb,
 * size-limit protected) and must never execute uploaded content.
 */
public interface ArchiveExtractor {

    /** @return true if this extractor can handle the given file based on signature/extension. */
    boolean supports(Path archiveFile);

    /**
     * Extracts the archive into destinationDir, enforcing SecurityGuard limits.
     * @return an ExtractionReport describing what was extracted / rejected.
     */
    ExtractionReport extract(Path archiveFile, Path destinationDir) throws IOException, ArchiveSecurityException;

    /** Human readable format name, e.g. "ZIP", "TAR.GZ". */
    String formatName();
}
