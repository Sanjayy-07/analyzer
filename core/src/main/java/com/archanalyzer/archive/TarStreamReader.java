package com.archanalyzer.archive;

import java.io.*;
import java.nio.file.*;

/**
 * Minimal, dependency-free reader for the POSIX "ustar" TAR format.
 * TAR has no external library requirement in the JDK, so unlike RAR/7Z we can
 * genuinely support it without pulling in commons-compress. GZIP wrapping is
 * handled by java.util.zip.GZIPInputStream (also JDK standard library), so
 * TAR and TAR.GZ/TGZ share this single reader.
 *
 * Only regular files and directories are materialized; symlinks, hard links,
 * device files, and other special TAR entry types are skipped and reported
 * as warnings (they have no legitimate place in a source-code archive and
 * are a common vector for extraction attacks).
 */
public final class TarStreamReader {

    private static final int BLOCK_SIZE = 512;

    private TarStreamReader() {}

    public static ExtractionReport read(InputStream rawIn, Path destinationDir, String formatLabel)
            throws IOException, ArchiveSecurityException {
        ArchiveSecurityGuard guard = new ArchiveSecurityGuard();
        guard.prepareDestination(destinationDir);
        ExtractionReport report = new ExtractionReport(formatLabel);

        DataInputStream in = new DataInputStream(new BufferedInputStream(rawIn));
        byte[] header = new byte[BLOCK_SIZE];
        int consecutiveZeroBlocks = 0;

        while (true) {
            int read = readFully(in, header);
            if (read < BLOCK_SIZE) break; // truncated/EOF

            if (isAllZero(header)) {
                consecutiveZeroBlocks++;
                if (consecutiveZeroBlocks >= 2) break; // two zero blocks = end of archive
                continue;
            }
            consecutiveZeroBlocks = 0;

            String name = readString(header, 0, 100);
            String prefix = readString(header, 345, 155);
            String fullName = prefix.isEmpty() ? name : prefix + "/" + name;
            long size = readOctal(header, 124, 12);
            char typeFlag = (char) header[156];

            guard.registerEntry(size);
            Path targetPath = guard.resolveSafeEntryPath(destinationDir, fullName);

            long paddedSize = ((size + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;

            if (typeFlag == '5' || fullName.endsWith("/")) {
                // Directory entry
                Files.createDirectories(targetPath);
                skipFully(in, paddedSize);
            } else if (typeFlag == '0' || typeFlag == '\0') {
                // Regular file
                if (targetPath.getParent() != null) Files.createDirectories(targetPath.getParent());
                try (OutputStream out = Files.newOutputStream(targetPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    long remaining = size;
                    byte[] buf = new byte[8192];
                    while (remaining > 0) {
                        int chunk = (int) Math.min(buf.length, remaining);
                        int n = in.read(buf, 0, chunk);
                        if (n < 0) throw new IOException("Unexpected EOF inside TAR entry: " + fullName);
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
                skipFully(in, paddedSize - size); // skip block padding
                report.fileExtracted(size);
            } else {
                // Symlink ('2'), hardlink ('1'), device, fifo, etc: skip content, do not materialize.
                report.warn("Skipped unsupported TAR entry type '" + typeFlag + "' for: " + fullName);
                skipFully(in, paddedSize);
            }
        }
        return report;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        byte[] buf = new byte[8192];
        while (remaining > 0) {
            int chunk = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, chunk);
            if (r < 0) return;
            remaining -= r;
        }
    }

    private static boolean isAllZero(byte[] header) {
        for (byte b : header) if (b != 0) return false;
        return true;
    }

    private static String readString(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) end++;
        return new String(header, offset, end - offset).trim();
    }

    private static long readOctal(byte[] header, int offset, int length) {
        String s = readString(header, offset, length).trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
