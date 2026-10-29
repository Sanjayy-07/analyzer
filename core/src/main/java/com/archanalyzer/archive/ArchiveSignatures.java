package com.archanalyzer.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects archive type from file signature ("magic bytes") rather than trusting
 * the uploaded filename/extension, which a hostile user could spoof.
 * PART 8: "Automatically detect archive type."
 */
public final class ArchiveSignatures {

    public static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};   // "PK\3\4"
    public static final byte[] ZIP_EMPTY_MAGIC = {0x50, 0x4B, 0x05, 0x06};
    public static final byte[] GZIP_MAGIC = {0x1F, (byte) 0x8B};
    public static final byte[] SEVEN_Z_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    public static final byte[] RAR4_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
    public static final byte[] RAR5_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00};

    private ArchiveSignatures() {}

    public static boolean matches(Path file, byte[] magic) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(magic.length);
            if (header.length < magic.length) return false;
            for (int i = 0; i < magic.length; i++) {
                if (header[i] != magic[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isTar(Path file) {
        // TAR has no magic number at offset 0; the reliable signature is the
        // "ustar" string at byte offset 257 (POSIX ustar format).
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(263);
            if (header.length < 263) return false;
            String magic = new String(header, 257, 5);
            return magic.equals("ustar");
        } catch (IOException e) {
            return false;
        }
    }
}
