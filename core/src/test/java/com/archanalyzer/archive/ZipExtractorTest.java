package com.archanalyzer.archive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ZipExtractorTest {

    private ZipExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ZipExtractor();
    }

    @Test
    void testExtractArchiveWithMultipleDirectoriesDoesNotTriggerBombSecurity(@TempDir Path tempDir) throws Exception {
        Path zipFile = tempDir.resolve("test-project.zip");

        // Create a ZIP with 10 directory entries and 2 small files
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 1; i <= 10; i++) {
                ZipEntry dirEntry = new ZipEntry("dir" + i + "/");
                zos.putNextEntry(dirEntry);
                zos.closeEntry();
            }
            ZipEntry fileEntry1 = new ZipEntry("dir1/Hello.java");
            zos.putNextEntry(fileEntry1);
            zos.write("public class Hello {}".getBytes());
            zos.closeEntry();

            ZipEntry fileEntry2 = new ZipEntry("dir2/World.java");
            zos.putNextEntry(fileEntry2);
            zos.write("public class World {}".getBytes());
            zos.closeEntry();
        }
        Files.write(zipFile, baos.toByteArray());

        Path destDir = tempDir.resolve("extracted");
        ExtractionReport report = extractor.extract(zipFile, destDir);

        assertNotNull(report);
        assertEquals("ZIP", report.getFormat());
        assertTrue(Files.exists(destDir.resolve("dir1/Hello.java")));
        assertTrue(Files.exists(destDir.resolve("dir2/World.java")));
        for (int i = 1; i <= 10; i++) {
            assertTrue(Files.isDirectory(destDir.resolve("dir" + i)));
        }
    }

    @Test
    void testZipSlipPathTraversalBlocked(@TempDir Path tempDir) throws Exception {
        ArchiveSecurityGuard guard = new ArchiveSecurityGuard();
        Path destDir = tempDir.resolve("target");
        Files.createDirectories(destDir);

        assertThrows(ArchiveSecurityException.class, () ->
            guard.resolveSafeEntryPath(destDir, "../escape.txt"));

        assertThrows(ArchiveSecurityException.class, () ->
            guard.resolveSafeEntryPath(destDir, "/etc/passwd"));

        assertThrows(ArchiveSecurityException.class, () ->
            guard.resolveSafeEntryPath(destDir, "C:\\Windows\\System32\\calc.exe"));
    }
}
