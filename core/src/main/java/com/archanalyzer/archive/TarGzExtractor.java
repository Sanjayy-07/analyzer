package com.archanalyzer.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * TAR.GZ / TGZ archives: gzip decompression via java.util.zip.GZIPInputStream
 * (JDK standard library), then the same dependency-free ustar reader used by
 * plain TAR. Detected by GZIP magic bytes (1F 8B) plus a .tar.gz/.tgz-style
 * name, since GZIP alone doesn't tell us what's inside.
 */
public class TarGzExtractor implements ArchiveExtractor {

    @Override
    public boolean supports(Path archiveFile) {
        String name = archiveFile.toString().toLowerCase();
        boolean gzipMagic = ArchiveSignatures.matches(archiveFile, ArchiveSignatures.GZIP_MAGIC);
        return gzipMagic && (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".gz"));
    }

    @Override
    public ExtractionReport extract(Path archiveFile, Path destinationDir)
            throws IOException, ArchiveSecurityException {
        try (InputStream fileIn = Files.newInputStream(archiveFile);
             GZIPInputStream gzIn = new GZIPInputStream(fileIn)) {
            return TarStreamReader.read(gzIn, destinationDir, formatName());
        }
    }

    @Override
    public String formatName() { return "TAR.GZ"; }
}
