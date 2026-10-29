package com.archanalyzer.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain (uncompressed) TAR archives. Detected via the "ustar" magic at offset 257. */
public class TarExtractor implements ArchiveExtractor {

    @Override
    public boolean supports(Path archiveFile) {
        return ArchiveSignatures.isTar(archiveFile)
            || archiveFile.toString().toLowerCase().endsWith(".tar");
    }

    @Override
    public ExtractionReport extract(Path archiveFile, Path destinationDir)
            throws IOException, ArchiveSecurityException {
        try (InputStream in = Files.newInputStream(archiveFile)) {
            return TarStreamReader.read(in, destinationDir, formatName());
        }
    }

    @Override
    public String formatName() { return "TAR"; }
}
