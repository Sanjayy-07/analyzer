package com.archanalyzer.pipeline;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.*;

/** PART 27: packages the final (possibly refactored) project directory into a real ZIP file. */
public class ProjectPackager {

    public void packageToZip(Path projectDir, Path outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            try (Stream<Path> walk = Files.walk(projectDir)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        String entryName = projectDir.relativize(file).toString().replace('\\', '/');
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
