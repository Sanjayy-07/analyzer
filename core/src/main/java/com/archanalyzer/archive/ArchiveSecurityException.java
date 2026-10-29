package com.archanalyzer.archive;

/**
 * Custom checked exception (Advanced Java Module 1: custom exception handling).
 * Thrown whenever an archive violates one of the ArchiveSecurityGuard rules
 * (path traversal, decompression bomb, oversized entry, symlink escape, ...).
 */
public class ArchiveSecurityException extends Exception {
    public ArchiveSecurityException(String message) {
        super(message);
    }
    public ArchiveSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
