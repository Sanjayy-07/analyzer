package com.archanalyzer.archive;

import java.util.ArrayList;
import java.util.List;

public class ExtractionReport {
    private final String format;
    private int filesExtracted = 0;
    private long totalBytes = 0;
    private final List<String> warnings = new ArrayList<>();

    public ExtractionReport(String format) { this.format = format; }

    public void fileExtracted(long size) { filesExtracted++; totalBytes += size; }
    public void warn(String message) { warnings.add(message); }

    public String getFormat() { return format; }
    public int getFilesExtracted() { return filesExtracted; }
    public long getTotalBytes() { return totalBytes; }
    public List<String> getWarnings() { return warnings; }

    @Override
    public String toString() {
        return String.format("ExtractionReport{format=%s, files=%d, bytes=%d, warnings=%d}",
            format, filesExtracted, totalBytes, warnings.size());
    }
}
