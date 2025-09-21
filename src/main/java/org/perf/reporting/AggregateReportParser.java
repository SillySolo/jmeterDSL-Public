// ============================================================================
// ENHANCED AGGREGATE REPORT DATA PARSER
// ============================================================================

package org.perf.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class AggregateReportParser {
    
    public static class SamplerStats {
        private String label;
        private int samples;
        private double average;
        private double median;
        private double percentile90;
        private double percentile95;
        private double percentile99;
        private double min;
        private double max;
        private double errorPercentage;
        private double throughput;
        private double receivedKBPerSec;
        private double sentKBPerSec;
        private long totalBytes;
        private long totalSentBytes;
        private int errorCount;
        
        // Getters and setters
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        
        public int getSamples() { return samples; }
        public void setSamples(int samples) { this.samples = samples; }
        
        public double getAverage() { return average; }
        public void setAverage(double average) { this.average = average; }
        
        public double getMedian() { return median; }
        public void setMedian(double median) { this.median = median; }
        
        public double getPercentile90() { return percentile90; }
        public void setPercentile90(double percentile90) { this.percentile90 = percentile90; }
        
        public double getPercentile95() { return percentile95; }
        public void setPercentile95(double percentile95) { this.percentile95 = percentile95; }
        
        public double getPercentile99() { return percentile99; }
        public void setPercentile99(double percentile99) { this.percentile99 = percentile99; }
        
        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
        
        public double getErrorPercentage() { return errorPercentage; }
        public void setErrorPercentage(double errorPercentage) { this.errorPercentage = errorPercentage; }
        
        public double getThroughput() { return throughput; }
        public void setThroughput(double throughput) { this.throughput = throughput; }
        
        public double getReceivedKBPerSec() { return receivedKBPerSec; }
        public void setReceivedKBPerSec(double receivedKBPerSec) { this.receivedKBPerSec = receivedKBPerSec; }
        
        public double getSentKBPerSec() { return sentKBPerSec; }
        public void setSentKBPerSec(double sentKBPerSec) { this.sentKBPerSec = sentKBPerSec; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public long getTotalSentBytes() { return totalSentBytes; }
        public void setTotalSentBytes(long totalSentBytes) { this.totalSentBytes = totalSentBytes; }
        
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    }


    public static List<SamplerStats> parseJtlFile(Path jtlFile) throws IOException {
        if (!Files.exists(jtlFile)) {
            System.err.println("JTL file does not exist: " + jtlFile);
            return new ArrayList<>();
        }
        
        List<String> lines = Files.readAllLines(jtlFile);
        if (lines.size() <= 1) {
            System.err.println("JTL file is empty or has only header: " + jtlFile);
            return new ArrayList<>();
        }
        
        try {
            // Parse header to get column indices
            String header = lines.get(0);
            System.out.println("JTL Header: " + header);
            
            String[] headerColumns = header.split(",");
            Map<String, Integer> columnMap = new HashMap<>();
            for (int i = 0; i < headerColumns.length; i++) {
                columnMap.put(headerColumns[i].trim(), i);
            }
            
            System.out.println("Available columns: " + columnMap.keySet());
            
            // Verify required columns exist
            if (!columnMap.containsKey("label") || !columnMap.containsKey("elapsed")) {
                System.err.println("JTL file missing required columns (label or elapsed)");
                return new ArrayList<>();
            }
            
            // Group data by label (sampler name)
            Map<String, List<SampleRecord>> samplerGroups = new HashMap<>();
            int parsedLines = 0;
            int skippedLines = 0;
            
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    skippedLines++;
                    continue;
                }
                
                String[] columns = line.split(",");
                
                if (columns.length >= headerColumns.length - 2) { // Allow some tolerance
                    SampleRecord record = parseSampleRecord(columns, columnMap);
                    if (record != null && record.label != null && !record.label.trim().isEmpty()) {
                        samplerGroups.computeIfAbsent(record.label, k -> new ArrayList<>()).add(record);
                        parsedLines++;
                    } else {
                        skippedLines++;
                    }
                } else {
                    skippedLines++;
                }
            }
            
            System.out.println("Parsed " + parsedLines + " lines, skipped " + skippedLines + " lines");
            System.out.println("Found " + samplerGroups.size() + " unique samplers: " + samplerGroups.keySet());
            
            // Calculate aggregate statistics for each sampler
            List<SamplerStats> aggregateStats = new ArrayList<>();
            for (Map.Entry<String, List<SampleRecord>> entry : samplerGroups.entrySet()) {
                SamplerStats stats = calculateAggregateStats(entry.getKey(), entry.getValue());
                aggregateStats.add(stats);
            }
            
            // Sort by label name
            aggregateStats.sort(Comparator.comparing(SamplerStats::getLabel));
            
            // Add TOTAL row if multiple samplers
            if (aggregateStats.size() > 1) {
                SamplerStats totalStats = calculateTotalStats(aggregateStats);
                aggregateStats.add(totalStats);
            }
            
            return aggregateStats;
            
        } catch (Exception e) {
            System.err.println("Error parsing JTL file: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to parse JTL file", e);
        }
    }
    
    private static SampleRecord parseSampleRecord(String[] columns, Map<String, Integer> columnMap) {
        try {
            SampleRecord record = new SampleRecord();
            
            record.timeStamp = getLongValue(columns, columnMap, "timeStamp");
            record.elapsed = getIntValue(columns, columnMap, "elapsed");
            record.label = getStringValue(columns, columnMap, "label");
            record.responseCode = getStringValue(columns, columnMap, "responseCode");
            record.success = getBooleanValue(columns, columnMap, "success");
            record.bytes = getLongValue(columns, columnMap, "bytes");
            record.sentBytes = getLongValue(columns, columnMap, "sentBytes");
            record.latency = getIntValue(columns, columnMap, "latency");
            
            return record;
        } catch (Exception e) {
            System.err.println("Error parsing sample record: " + e.getMessage());
            return null;
        }
    }
    
    private static String getStringValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
        Integer index = columnMap.get(columnName);
        if (index != null && index < columns.length) {
            return columns[index].trim().replaceAll("\"", ""); // Remove quotes
        }
        return "";
    }
    
    private static long getLongValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
        String value = getStringValue(columns, columnMap, columnName);
        try {
            return value.isEmpty() ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static int getIntValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
        return (int) getLongValue(columns, columnMap, columnName);
    }
    
    private static boolean getBooleanValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
        String value = getStringValue(columns, columnMap, columnName);
        return "true".equalsIgnoreCase(value);
    }

    // public static List<SamplerStats> parseJtlFile(Path jtlFile) throws IOException {
    //     if (!Files.exists(jtlFile)) {
    //         return new ArrayList<>();
    //     }
        
    //     List<String> lines = Files.readAllLines(jtlFile);
    //     if (lines.size() <= 1) { // Only header or empty
    //         return new ArrayList<>();
    //     }
        
    //     // Parse header to get column indices
    //     String header = lines.get(0);
    //     String[] headerColumns = header.split(",");
    //     Map<String, Integer> columnMap = new HashMap<>();
    //     for (int i = 0; i < headerColumns.length; i++) {
    //         columnMap.put(headerColumns[i].trim(), i);
    //     }
        
    //     // Group data by label (sampler name)
    //     Map<String, List<SampleRecord>> samplerGroups = new HashMap<>();
        
    //     for (int i = 1; i < lines.size(); i++) {
    //         String line = lines.get(i);
    //         String[] columns = line.split(",");
            
    //         if (columns.length >= headerColumns.length) {
    //             SampleRecord record = parseSampleRecord(columns, columnMap);
    //             if (record != null) {
    //                 samplerGroups.computeIfAbsent(record.label, k -> new ArrayList<>()).add(record);
    //             }
    //         }
    //     }
        
    //     // Calculate aggregate statistics for each sampler
    //     List<SamplerStats> aggregateStats = new ArrayList<>();
    //     for (Map.Entry<String, List<SampleRecord>> entry : samplerGroups.entrySet()) {
    //         SamplerStats stats = calculateAggregateStats(entry.getKey(), entry.getValue());
    //         aggregateStats.add(stats);
    //     }
        
    //     // Sort by label name
    //     aggregateStats.sort(Comparator.comparing(SamplerStats::getLabel));
        
    //     // Add TOTAL row
    //     if (aggregateStats.size() > 1) {
    //         SamplerStats totalStats = calculateTotalStats(aggregateStats);
    //         aggregateStats.add(totalStats);
    //     }
        
    //     return aggregateStats;
    // }
    
    // private static SampleRecord parseSampleRecord(String[] columns, Map<String, Integer> columnMap) {
    //     try {
    //         SampleRecord record = new SampleRecord();
            
    //         record.timeStamp = getLongValue(columns, columnMap, "timeStamp");
    //         record.elapsed = getIntValue(columns, columnMap, "elapsed");
    //         record.label = getStringValue(columns, columnMap, "label");
    //         record.responseCode = getStringValue(columns, columnMap, "responseCode");
    //         record.success = getBooleanValue(columns, columnMap, "success");
    //         record.bytes = getLongValue(columns, columnMap, "bytes");
    //         record.sentBytes = getLongValue(columns, columnMap, "sentBytes");
    //         record.latency = getIntValue(columns, columnMap, "latency");
            
    //         return record;
    //     } catch (Exception e) {
    //         System.err.println("Error parsing sample record: " + e.getMessage());
    //         return null;
    //     }
    // }
    
    // private static String getStringValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
    //     Integer index = columnMap.get(columnName);
    //     if (index != null && index < columns.length) {
    //         return columns[index].trim();
    //     }
    //     return "";
    // }
    
    // private static long getLongValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
    //     String value = getStringValue(columns, columnMap, columnName);
    //     try {
    //         return value.isEmpty() ? 0 : Long.parseLong(value);
    //     } catch (NumberFormatException e) {
    //         return 0;
    //     }
    // }
    
    // private static int getIntValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
    //     return (int) getLongValue(columns, columnMap, columnName);
    // }
    
    // private static boolean getBooleanValue(String[] columns, Map<String, Integer> columnMap, String columnName) {
    //     String value = getStringValue(columns, columnMap, columnName);
    //     return "true".equalsIgnoreCase(value);
    // }
    
    private static SamplerStats calculateAggregateStats(String label, List<SampleRecord> records) {
        SamplerStats stats = new SamplerStats();
        stats.setLabel(label);
        
        if (records.isEmpty()) {
            return stats;
        }
        
        // Basic counts
        stats.setSamples(records.size());
        long errorCount = records.stream().mapToLong(r -> r.success ? 0 : 1).sum();
        stats.setErrorCount((int) errorCount);
        stats.setErrorPercentage(records.size() > 0 ? (double) errorCount / records.size() * 100 : 0);
        
        // Response time statistics
        List<Integer> responseTimes = records.stream().map(r -> r.elapsed).collect(Collectors.toList());
        Collections.sort(responseTimes);
        
        stats.setMin(responseTimes.get(0));
        stats.setMax(responseTimes.get(responseTimes.size() - 1));
        stats.setAverage(responseTimes.stream().mapToDouble(Integer::doubleValue).average().orElse(0));
        stats.setMedian(calculatePercentile(responseTimes, 50));
        stats.setPercentile90(calculatePercentile(responseTimes, 90));
        stats.setPercentile95(calculatePercentile(responseTimes, 95));
        stats.setPercentile99(calculatePercentile(responseTimes, 99));
        
        // Throughput and data transfer statistics
        long totalBytes = records.stream().mapToLong(r -> r.bytes).sum();
        long totalSentBytes = records.stream().mapToLong(r -> r.sentBytes).sum();
        stats.setTotalBytes(totalBytes);
        stats.setTotalSentBytes(totalSentBytes);
        
        // Calculate throughput (requests per second)
        if (!records.isEmpty()) {
            long minTime = records.stream().mapToLong(r -> r.timeStamp).min().orElse(0);
            long maxTime = records.stream().mapToLong(r -> r.timeStamp + r.elapsed).max().orElse(0);
            double durationSeconds = (maxTime - minTime) / 1000.0;
            
            if (durationSeconds > 0) {
                stats.setThroughput(records.size() / durationSeconds);
                stats.setReceivedKBPerSec((totalBytes / 1024.0) / durationSeconds);
                stats.setSentKBPerSec((totalSentBytes / 1024.0) / durationSeconds);
            }
        }
        
        return stats;
    }
    
    private static double calculatePercentile(List<Integer> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;
        
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }
        
        double weight = index - lowerIndex;
        return sortedValues.get(lowerIndex) * (1 - weight) + sortedValues.get(upperIndex) * weight;
    }
    
    private static SamplerStats calculateTotalStats(List<SamplerStats> allStats) {
        SamplerStats totalStats = new SamplerStats();
        totalStats.setLabel("TOTAL");
        
        int totalSamples = allStats.stream().mapToInt(SamplerStats::getSamples).sum();
        int totalErrors = allStats.stream().mapToInt(SamplerStats::getErrorCount).sum();
        long totalBytes = allStats.stream().mapToLong(SamplerStats::getTotalBytes).sum();
        long totalSentBytes = allStats.stream().mapToLong(SamplerStats::getTotalSentBytes).sum();
        
        totalStats.setSamples(totalSamples);
        totalStats.setErrorCount(totalErrors);
        totalStats.setErrorPercentage(totalSamples > 0 ? (double) totalErrors / totalSamples * 100 : 0);
        totalStats.setTotalBytes(totalBytes);
        totalStats.setTotalSentBytes(totalSentBytes);
        
        // Weighted averages
        double weightedAverage = allStats.stream()
            .mapToDouble(s -> s.getAverage() * s.getSamples())
            .sum() / totalSamples;
        totalStats.setAverage(weightedAverage);
        
        // Min/Max across all samplers
        totalStats.setMin(allStats.stream().mapToDouble(SamplerStats::getMin).min().orElse(0));
        totalStats.setMax(allStats.stream().mapToDouble(SamplerStats::getMax).max().orElse(0));
        
        // Sum throughputs
        totalStats.setThroughput(allStats.stream().mapToDouble(SamplerStats::getThroughput).sum());
        totalStats.setReceivedKBPerSec(allStats.stream().mapToDouble(SamplerStats::getReceivedKBPerSec).sum());
        totalStats.setSentKBPerSec(allStats.stream().mapToDouble(SamplerStats::getSentKBPerSec).sum());
        
        return totalStats;
    }
    
    private static class SampleRecord {
        long timeStamp;
        int elapsed;
        String label;
        String responseCode;
        boolean success;
        long bytes;
        long sentBytes;
        int latency;
    }
}