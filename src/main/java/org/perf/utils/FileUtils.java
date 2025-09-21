package org.perf.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;

public class FileUtils {
    
    public static Path createTimestampedResultsDir() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path resultsDir = Paths.get("target", "results_" + timestamp);
        Files.createDirectories(resultsDir);
        return resultsDir;
    }
    
    public static Path createResultsDir(String dirName) throws IOException {
        Path resultsDir = Paths.get("target", dirName);
        Files.createDirectories(resultsDir);
        return resultsDir;
    }
}