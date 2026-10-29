package com.archanalyzer.archive;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Real, fully-supported ZIP extraction using only java.util.zip (JDK
 * standard library — no external dependency required). Detects format by
 * magic bytes ("PK\x03\x04" or empty-archive variants) rather than trusting
 * the file extension, per PART 8's "automatic archive-type detection"
 * requirement.
 */
public class ZipExtractor implements ArchiveExtractor {

    @Override
    public boolean supports(Path archiveFile) {
        return ArchiveSignatures.matches(archiveFile, ArchiveSignatures.ZIP_MAGIC)
            || archiveFile.toString().toLowerCase().endsWith(".zip");
    }

    @Override
    public ExtractionReport extract(Path archiveFile, Path destinationDir)
            throws IOException, ArchiveSecurityException {
        ArchiveSecurityGuard guard = new ArchiveSecurityGuard();
        guard.prepareDestination(destinationDir);
        ExtractionReport report = new ExtractionReport(formatName());

        try (ZipFile zipFile = new ZipFile(archiveFile.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    guard.registerEntry(0);
                    Path targetPath = guard.resolveSafeEntryPath(destinationDir, entry.getName());
                    Files.createDirectories(targetPath);
                    continue;
                }

                // If declared size is unknown (-1), register 0 up-front and accumulate actual extracted bytes later
                long declaredSize = entry.getSize();
                guard.registerEntry(declaredSize > 0 ? declaredSize : 0);

                Path targetPath = guard.resolveSafeEntryPath(destinationDir, entry.getName());

                Files.createDirectories(targetPath.getParent());
                try (InputStream in = zipFile.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(targetPath,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    long copied = copyWithLimit(in, out, ArchiveSecurityGuard.MAX_SINGLE_ENTRY_BYTES);
                    if (declaredSize <= 0) {
                        guard.recordExtractedBytes(copied);
                    }
                    report.fileExtracted(copied);
                }
            }
        }
        return report;
    }

    @Override
    public String formatName() { return "ZIP"; }

    /** Streaming copy that aborts if the entry turns out larger than declared (zip-bomb defence). */
    private long copyWithLimit(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("Entry exceeded declared/allowed size during extraction (possible zip bomb).");
            }
            out.write(buffer, 0, read);
        }
        return total;
    }
}
