package com.archanalyzer.archive;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * FACTORY PATTERN (PART 27 of the spec).
 * ---------------------------------------------------------------------------
 * Chooses the correct ArchiveExtractor implementation based on file
 * signature, not filename, and reports honestly on formats we cannot safely
 * support with dependency-free Java.
 *
 * HONESTY NOTE (PART 34 / PART 8: "Do not pretend every format is
 * supported"): RAR and 7Z use proprietary/complex compression algorithms
 * (RAR: proprietary; 7Z: LZMA/LZMA2). Implementing a correct, safe decoder
 * for either by hand is not realistic, and the JDK ships no support for
 * them. Real support requires an external library (e.g. Apache
 * Commons Compress + XZ for Java for 7Z, or junrar for RAR) added to the
 * Maven build with internet access. Until then, this factory reports those
 * formats as UNSUPPORTED with a clear message rather than silently failing
 * or faking a result. ISO is similarly out of scope: it is a disk-image
 * format, not a compression format, and parsing ISO9660 correctly is a
 * project of its own.
 */
public final class ArchiveExtractorFactory {

    private final List<ArchiveExtractor> extractors = List.of(
        new ZipExtractor(),
        new TarGzExtractor(), // check before TarExtractor: both may look "tar-ish" once decompressed
        new TarExtractor()
    );

    public static final List<String> KNOWN_UNSUPPORTED_FORMATS = List.of("RAR", "7Z", "ISO");

    public Optional<ArchiveExtractor> resolve(Path archiveFile) {
        return extractors.stream().filter(e -> e.supports(archiveFile)).findFirst();
    }

    /** Best-effort human-readable format identification, used to produce clear error messages. */
    public String identifyLikelyFormat(Path archiveFile) {
        if (ArchiveSignatures.matches(archiveFile, ArchiveSignatures.SEVEN_Z_MAGIC)) return "7Z";
        if (ArchiveSignatures.matches(archiveFile, ArchiveSignatures.RAR4_MAGIC)
            || ArchiveSignatures.matches(archiveFile, ArchiveSignatures.RAR5_MAGIC)) return "RAR";
        String name = archiveFile.toString().toLowerCase();
        if (name.endsWith(".iso")) return "ISO";
        return "UNKNOWN";
    }

    public boolean isKnownUnsupported(String formatName) {
        return KNOWN_UNSUPPORTED_FORMATS.contains(formatName);
    }
}
