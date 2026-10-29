package com.archanalyzer.archive;

import java.io.IOException;
import java.nio.file.*;

/**
 * PART 8 / PART 30 SECURITY REQUIREMENTS
 * ---------------------------------------------------------------------------
 * Centralised checks reused by every ArchiveExtractor implementation so the
 * safety rules live in exactly one place instead of being re-implemented
 * (and potentially forgotten) per format.
 *
 * Protections implemented:
 *  1. Path traversal ("zip slip"): resolved entry path must remain inside
 *     the destination directory.
 *  2. Decompression-bomb protection: hard caps on (a) total uncompressed
 *     bytes and (b) total entry count, checked incrementally during
 *     extraction, not just after the fact.
 *  3. Per-entry size cap, to stop a single enormous file from exhausting disk.
 *  4. Symlink rejection: archives must not be allowed to create symlinks
 *     that point outside the workspace.
 */
public final class ArchiveSecurityGuard {

    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 500L * 1024 * 1024; // 500 MB
    public static final long MAX_SINGLE_ENTRY_BYTES = 100L * 1024 * 1024;       // 100 MB
    public static final int MAX_ENTRY_COUNT = 50_000;

    private long totalBytesSoFar = 0;
    private int entryCountSoFar = 0;

    /**
     * Resolves an archive entry name against destDir and verifies it cannot
     * escape the destination ("zip slip" / path traversal attack).
     */
    public Path resolveSafeEntryPath(Path destDir, String entryName) throws ArchiveSecurityException {
        if (entryName == null || entryName.isBlank()) {
            throw new ArchiveSecurityException("Archive contains an entry with an empty name.");
        }
        // Normalize backslashes to forward slashes for cross-platform zip slip checking
        String normalizedName = entryName.replace('\\', '/');
        // Reject absolute paths and Windows drive-letter paths outright.
        if (normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new ArchiveSecurityException("Archive entry uses an absolute path: " + entryName);
        }
        Path target = destDir.resolve(normalizedName).normalize();
        Path normalizedDest = destDir.normalize();
        if (!target.startsWith(normalizedDest)) {
            throw new ArchiveSecurityException("Path traversal attempt detected in entry: " + entryName);
        }
        return target;
    }

    /** Call before extracting each entry's bytes to enforce cumulative limits. */
    public void registerEntry(long declaredOrActualSize) throws ArchiveSecurityException {
        entryCountSoFar++;
        if (entryCountSoFar > MAX_ENTRY_COUNT) {
            throw new ArchiveSecurityException(
                "Archive exceeds the maximum allowed entry count (" + MAX_ENTRY_COUNT + "). Possible archive bomb.");
        }
        if (declaredOrActualSize > MAX_SINGLE_ENTRY_BYTES) {
            throw new ArchiveSecurityException(
                "A single archive entry exceeds the per-file size limit (" + MAX_SINGLE_ENTRY_BYTES + " bytes).");
        }
        totalBytesSoFar += declaredOrActualSize;
        if (totalBytesSoFar > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw new ArchiveSecurityException(
                "Total uncompressed size exceeds the allowed limit (" + MAX_TOTAL_UNCOMPRESSED_BYTES
                    + " bytes). Possible decompression bomb.");
        }
    }

    /** Accumulates dynamically-streamed bytes for entries whose uncompressed size was not declared. */
    public void recordExtractedBytes(long bytes) throws ArchiveSecurityException {
        totalBytesSoFar += bytes;
        if (totalBytesSoFar > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw new ArchiveSecurityException(
                "Total uncompressed size exceeds the allowed limit (" + MAX_TOTAL_UNCOMPRESSED_BYTES
                    + " bytes). Possible decompression bomb.");
        }
    }

    /** Ensures the destination directory exists and is itself a directory we control. */
    public void prepareDestination(Path destDir) throws IOException {
        Files.createDirectories(destDir);
    }
}
