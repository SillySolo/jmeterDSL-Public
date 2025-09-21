// ============================================================================
// CONSOLIDATED REPORT MANAGER
// ============================================================================

package org.perf.reporting;

import org.perf.model.ExecutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ConsolidatedReportManager {
    private static final ConsolidatedReportManager INSTANCE = new ConsolidatedReportManager();
    private final List<TestResultEntry> allResults = new CopyOnWriteArrayList<>();
    private final ReentrantLock reportLock = new ReentrantLock();
    private Path consolidatedReportDir;
    private Path consolidatedJtlFile;
    
    private ConsolidatedReportManager() {
        initializeReportDirectory();
    }
    
    public static ConsolidatedReportManager getInstance() {
        return INSTANCE;
    }
    
    private void initializeReportDirectory() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            consolidatedReportDir = Paths.get("target", "consolidated-results-" + timestamp);
            Files.createDirectories(consolidatedReportDir);
            consolidatedJtlFile = consolidatedReportDir.resolve("consolidated-results.jtl");
            
            // Create JTL header
            String jtlHeader = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Filename,latency,encoding,SampleCount,ErrorCount,Hostname,IdleTime,Connect\n";
            Files.write(consolidatedJtlFile, jtlHeader.getBytes(), StandardOpenOption.CREATE);
            
            System.out.println("📊 Consolidated report directory: " + consolidatedReportDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize consolidated report directory", e);
        }
    }
    
    public synchronized void addTestResult(String testName, ExecutionResult result) {
        TestResultEntry entry = new TestResultEntry(
            testName,
            Thread.currentThread().getName(),
            result,
            LocalDateTime.now()
        );
        allResults.add(entry);
        
        // Append to consolidated JTL file
        appendToConsolidatedJtl(entry);
        
        System.out.println("📈 Added result for: " + testName + " (Total tests: " + allResults.size() + ")");
    }
    
    private void appendToConsolidatedJtl(TestResultEntry entry) {
        try {
            // Copy individual JTL content to consolidated file (skip header)
            Path individualJtl = entry.result.getConfig().getResultsDirectory().resolve("results.jtl");
            if (Files.exists(individualJtl)) {
                List<String> lines = Files.readAllLines(individualJtl);
                if (lines.size() > 1) { // Skip header line
                    for (int i = 1; i < lines.size(); i++) {
                        String line = lines.get(i);
                        // Prefix each line with test name for identification
                        String enhancedLine = line.replaceFirst("^([^,]+),", "$1," + entry.testName + "-");
                        Files.write(consolidatedJtlFile, (enhancedLine + "\n").getBytes(), StandardOpenOption.APPEND);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to append to consolidated JTL: " + e.getMessage());
        }
    }
    
    public synchronized void generateConsolidatedReport() {
        reportLock.lock();
        try {
            if (allResults.isEmpty()) {
                System.out.println("⚠️ No test results to consolidate");
                return;
            }

            try {
                generateConsolidatedSummary();
                generateConsolidatedCsv();
            } catch (IOException e) {
                System.err.println("Failed to generate consolidated summary or CSV: " + e.getMessage());
            }
            generateConsolidatedHtmlReport();

            System.out.println("✅ Consolidated report generated successfully!");
            System.out.println("📁 Location: " + consolidatedReportDir);
            System.out.println("📊 Summary: " + consolidatedReportDir.resolve("consolidated-summary.html"));

        } finally {
            reportLock.unlock();
        }
    }
    
    private void generateConsolidatedSummary() throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("<!DOCTYPE html><html><head><title>Consolidated Test Report</title>");
        summary.append("<style>");
        summary.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        summary.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        summary.append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        summary.append("th { background-color: #f2f2f2; font-weight: bold; }");
        summary.append(".success { color: green; font-weight: bold; }");
        summary.append(".error { color: red; font-weight: bold; }");
        summary.append(".warning { color: orange; font-weight: bold; }");
        summary.append(".summary-card { background: #f9f9f9; padding: 20px; margin: 10px 0; border-radius: 8px; }");
        summary.append("</style></head><body>");
        
        summary.append("<h1>🎯 Consolidated Performance Test Report</h1>");
        summary.append("<p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>");
        summary.append("<p><strong>Total Tests:</strong> ").append(allResults.size()).append("</p>");
        
        // Overall summary
        long totalSamples = allResults.stream().mapToLong(e -> e.result.getTotalSamples()).sum();
        long totalErrors = allResults.stream().mapToLong(e -> e.result.getErrorCount()).sum();
        double avgResponseTime = allResults.stream().mapToDouble(e -> e.result.getMeanResponseTime().toMillis()).average().orElse(0.0);
        
        summary.append("<div class='summary-card'>");
        summary.append("<h2>📊 Overall Summary</h2>");
        summary.append("<p><strong>Total Samples:</strong> ").append(totalSamples).append("</p>");
        summary.append("<p><strong>Total Errors:</strong> <span class='").append(totalErrors > 0 ? "error" : "success").append("'>").append(totalErrors).append("</span></p>");
        summary.append("<p><strong>Overall Error Rate:</strong> <span class='").append(totalErrors > 0 ? "error" : "success").append("'>").append(String.format("%.2f%%", (double) totalErrors / totalSamples * 100)).append("</span></p>");
        summary.append("<p><strong>Average Response Time:</strong> ").append(String.format("%.0fms", avgResponseTime)).append("</p>");
        summary.append("</div>");
        
        // Individual test results table
        summary.append("<h2>📋 Individual Test Results</h2>");
        summary.append("<table>");
        summary.append("<tr><th>Test Name</th><th>Thread</th><th>Execution Time</th><th>Samples</th><th>Errors</th><th>Error Rate</th><th>Mean Response Time</th><th>Status</th></tr>");
        
        for (TestResultEntry entry : allResults) {
            String status = entry.result.getErrorCount() == 0 ? "✅ PASSED" : "❌ FAILED";
            String statusClass = entry.result.getErrorCount() == 0 ? "success" : "error";
            
            summary.append("<tr>");
            summary.append("<td>").append(entry.testName).append("</td>");
            summary.append("<td>").append(entry.threadName).append("</td>");
            summary.append("<td>").append(entry.executionTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</td>");
            summary.append("<td>").append(entry.result.getTotalSamples()).append("</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(entry.result.getErrorCount()).append("</span></td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(String.format("%.2f%%", entry.result.getErrorPercentage())).append("</span></td>");
            summary.append("<td>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(status).append("</span></td>");
            summary.append("</tr>");
        }
        
        summary.append("</table>");
        summary.append("<hr><p><i>Report generated by JMeter DSL Framework</i></p>");
        summary.append("</body></html>");
        
        Files.write(consolidatedReportDir.resolve("consolidated-summary.html"), summary.toString().getBytes());
    }
    
    private void generateConsolidatedHtmlReport() {
        try {
            // Generate JMeter HTML report from consolidated JTL
            String jmeterHome = System.getProperty("jmeter.home", "/usr/local/bin/jmeter");
            Path htmlReportDir = consolidatedReportDir.resolve("jmeter-html-report");
            
            ProcessBuilder pb = new ProcessBuilder(
                jmeterHome + "/bin/jmeter",
                "-g", consolidatedJtlFile.toString(),
                "-o", htmlReportDir.toString()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                System.out.println("📊 JMeter HTML report generated: " + htmlReportDir);
            } else {
                System.out.println("⚠️ JMeter HTML report generation skipped (JMeter not found or error occurred)");
            }
        } catch (Exception e) {
            System.out.println("⚠️ JMeter HTML report generation skipped: " + e.getMessage());
        }
    }
    
    private void generateConsolidatedCsv() throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("Test Name,Thread Name,Execution Time,Total Samples,Error Count,Error Rate %,Mean Response Time (ms),Min Response Time (ms),Max Response Time (ms),Status\n");
        
        for (TestResultEntry entry : allResults) {
            csv.append(entry.testName).append(",");
            csv.append(entry.threadName).append(",");
            csv.append(entry.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(",");
            csv.append(entry.result.getTotalSamples()).append(",");
            csv.append(entry.result.getErrorCount()).append(",");
            csv.append(String.format("%.2f", entry.result.getErrorPercentage())).append(",");
            csv.append(entry.result.getMeanResponseTime().toMillis()).append(",");
            csv.append(entry.result.getMinResponseTime().toMillis()).append(",");
            csv.append(entry.result.getMaxResponseTime().toMillis()).append(",");
            csv.append(entry.result.getErrorCount() == 0 ? "PASSED" : "FAILED").append("\n");
        }
        
        Files.write(consolidatedReportDir.resolve("consolidated-results.csv"), csv.toString().getBytes());
    }
    
    public Path getConsolidatedReportDir() {
        return consolidatedReportDir;
    }
    
    public void reset() {
        allResults.clear();
        initializeReportDirectory();
    }
    
    private static class TestResultEntry {
        final String testName;
        final String threadName;
        final ExecutionResult result;
        final LocalDateTime executionTime;
        
        TestResultEntry(String testName, String threadName, ExecutionResult result, LocalDateTime executionTime) {
            this.testName = testName;
            this.threadName = threadName;
            this.result = result;
            this.executionTime = executionTime;
        }
    }
}