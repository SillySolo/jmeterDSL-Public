// ============================================================================
// ENHANCED CONSOLIDATED REPORT MANAGER - WITH INDIVIDUAL REPORTS
// THIS CODE DOEES NOT HAVE THE AGGREGATE REPORT PARSING FUNCTIONALITY
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

public class OLD_EnhancedConsolidatedReportManager {
    private static final OLD_EnhancedConsolidatedReportManager INSTANCE = new OLD_EnhancedConsolidatedReportManager();
    private final List<TestResultEntry> allResults = new CopyOnWriteArrayList<>();
    private final ReentrantLock reportLock = new ReentrantLock();
    private Path consolidatedReportDir;
    private Path consolidatedJtlFile;
    private final String sessionTimestamp;
    
    private OLD_EnhancedConsolidatedReportManager() {
        sessionTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        initializeReportDirectory();
    }
    
    public static OLD_EnhancedConsolidatedReportManager getInstance() {
        return INSTANCE;
    }
    
    private void initializeReportDirectory() {
        try {
            consolidatedReportDir = Paths.get("target", "test-session-" + sessionTimestamp);
            Files.createDirectories(consolidatedReportDir);
            Files.createDirectories(consolidatedReportDir.resolve("individual-reports"));
            Files.createDirectories(consolidatedReportDir.resolve("consolidated-report"));
            
            consolidatedJtlFile = consolidatedReportDir.resolve("consolidated-report").resolve("all-tests-combined.jtl");
            
            // Create JTL header
            String jtlHeader = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Filename,latency,encoding,SampleCount,ErrorCount,Hostname,IdleTime,Connect\n";
            Files.write(consolidatedJtlFile, jtlHeader.getBytes(), StandardOpenOption.CREATE);
            
            System.out.println("📊 Test session directory: " + consolidatedReportDir);
            System.out.println("📁 Individual reports: " + consolidatedReportDir.resolve("individual-reports"));
            System.out.println("📋 Consolidated report: " + consolidatedReportDir.resolve("consolidated-report"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize report directories", e);
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
        
        // Copy individual report to session directory
        copyIndividualReport(entry);
        
        // Append to consolidated JTL file
        appendToConsolidatedJtl(entry);
        
        System.out.println("📈 Added result for: " + testName + " | Individual: ✅ | Consolidated: ✅ | Total tests: " + allResults.size());
    }
    
    private void copyIndividualReport(TestResultEntry entry) {
        try {
            Path sourceDir = entry.result.getConfig().getResultsDirectory();
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            Path targetDir = consolidatedReportDir.resolve("individual-reports").resolve(safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-"));
            
            if (Files.exists(sourceDir)) {
                copyDirectory(sourceDir, targetDir);
                
                // Create a summary file for this individual test
                createIndividualTestSummary(entry, targetDir);
                
                System.out.println("📄 Individual report copied: " + targetDir.getFileName());
            }
        } catch (IOException e) {
            System.err.println("Failed to copy individual report for " + entry.testName + ": " + e.getMessage());
        }
    }
    
    private void createIndividualTestSummary(TestResultEntry entry, Path reportDir) throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("<!DOCTYPE html><html><head><title>").append(entry.testName).append(" - Individual Report</title>");
        summary.append("<style>");
        summary.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }");
        summary.append(".container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
        summary.append("h1 { color: #333; border-bottom: 3px solid #007acc; padding-bottom: 10px; }");
        summary.append(".metric-card { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #007acc; }");
        summary.append(".metric-value { font-size: 24px; font-weight: bold; color: #007acc; }");
        summary.append(".metric-label { color: #666; font-size: 14px; }");
        summary.append(".success { color: #28a745; }");
        summary.append(".error { color: #dc3545; }");
        summary.append(".warning { color: #ffc107; }");
        summary.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }");
        summary.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }");
        summary.append("th { background-color: #007acc; color: white; }");
        summary.append("</style></head><body>");
        
        summary.append("<div class='container'>");
        summary.append("<h1>🎯 ").append(entry.testName).append("</h1>");
        
        // Test metadata
        summary.append("<table>");
        summary.append("<tr><th>Property</th><th>Value</th></tr>");
        summary.append("<tr><td><strong>Thread Name</strong></td><td>").append(entry.threadName).append("</td></tr>");
        summary.append("<tr><td><strong>Execution Time</strong></td><td>").append(entry.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</td></tr>");
        summary.append("<tr><td><strong>Base URL</strong></td><td>").append(entry.result.getConfig().getBaseUrl()).append("</td></tr>");
        summary.append("</table>");
        
        // Key metrics
        summary.append("<h2>📊 Key Metrics</h2>");
        summary.append("<div style='display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px;'>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value'>").append(entry.result.getTotalSamples()).append("</div>");
        summary.append("<div class='metric-label'>Total Samples</div>");
        summary.append("</div>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value ").append(entry.result.getErrorCount() == 0 ? "success" : "error").append("'>").append(entry.result.getErrorCount()).append("</div>");
        summary.append("<div class='metric-label'>Errors</div>");
        summary.append("</div>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value'>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</div>");
        summary.append("<div class='metric-label'>Mean Response Time</div>");
        summary.append("</div>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value ").append(entry.result.getErrorPercentage() == 0 ? "success" : "error").append("'>").append(String.format("%.2f%%", entry.result.getErrorPercentage())).append("</div>");
        summary.append("<div class='metric-label'>Error Rate</div>");
        summary.append("</div>");
        
        summary.append("</div>");
        
        // Response time details
        summary.append("<h2>⏱️ Response Time Analysis</h2>");
        summary.append("<table>");
        summary.append("<tr><th>Metric</th><th>Value</th></tr>");
        summary.append("<tr><td>Minimum</td><td>").append(entry.result.getMinResponseTime().toMillis()).append("ms</td></tr>");
        summary.append("<tr><td>Mean</td><td>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</td></tr>");
        summary.append("<tr><td>Median</td><td>").append(entry.result.getMedianResponseTime().toMillis()).append("ms</td></tr>");
        summary.append("<tr><td>Maximum</td><td>").append(entry.result.getMaxResponseTime().toMillis()).append("ms</td></tr>");
        summary.append("</table>");
        
        // Links to detailed reports
        summary.append("<h2>🔗 Detailed Reports</h2>");
        summary.append("<ul>");
        if (Files.exists(reportDir.resolve("results.jtl"))) {
            summary.append("<li><a href='results.jtl'>📊 Raw JTL Results</a></li>");
        }
        if (Files.exists(reportDir.resolve("html-report").resolve("index.html"))) {
            summary.append("<li><a href='html-report/index.html'>📈 JMeter HTML Dashboard</a></li>");
        }
        summary.append("<li><a href='../consolidated-report/consolidated-summary.html'>📋 Back to Consolidated Report</a></li>");
        summary.append("</ul>");
        
        summary.append("<hr><p><i>Individual report generated by JMeter DSL Framework</i></p>");
        summary.append("</div></body></html>");
        
        Files.write(reportDir.resolve("test-summary.html"), summary.toString().getBytes());
    }
    
    private void appendToConsolidatedJtl(TestResultEntry entry) {
        try {
            Path individualJtl = entry.result.getConfig().getResultsDirectory().resolve("results.jtl");
            if (Files.exists(individualJtl)) {
                List<String> lines = Files.readAllLines(individualJtl);
                if (lines.size() > 1) {
                    for (int i = 1; i < lines.size(); i++) {
                        String line = lines.get(i);
                        // Enhance each line with test name prefix
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
                generateSessionIndex();
            } catch (IOException e) {
                System.err.println("Failed to generate consolidated report files: " + e.getMessage());
            }

            System.out.println("\n" + "=".repeat(100));
            System.out.println("🎉 DUAL REPORTING COMPLETE!");
            System.out.println("=".repeat(100));
            System.out.println("📁 Session Directory: " + consolidatedReportDir);
            System.out.println("🏠 Main Index: " + consolidatedReportDir.resolve("index.html"));
            System.out.println("📊 Consolidated Summary: " + consolidatedReportDir.resolve("consolidated-report/consolidated-summary.html"));
            System.out.println("📂 Individual Reports: " + consolidatedReportDir.resolve("individual-reports"));
            System.out.println("=".repeat(100));

        } finally {
            reportLock.unlock();
        }
    }
    
    private void generateSessionIndex() throws IOException {
        StringBuilder index = new StringBuilder();
        index.append("<!DOCTYPE html><html><head><title>Test Session Report - ").append(sessionTimestamp).append("</title>");
        index.append("<style>");
        index.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }");
        index.append(".header { background: rgba(255,255,255,0.1); padding: 30px; text-align: center; color: white; }");
        index.append(".container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }");
        index.append(".card { background: white; border-radius: 15px; padding: 30px; margin: 20px 0; box-shadow: 0 8px 32px rgba(0,0,0,0.1); }");
        index.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }");
        index.append(".metric-card { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; text-align: center; padding: 20px; border-radius: 10px; }");
        index.append(".metric-value { font-size: 2.5em; font-weight: bold; margin-bottom: 10px; }");
        index.append(".btn { display: inline-block; padding: 12px 24px; background: #007acc; color: white; text-decoration: none; border-radius: 25px; margin: 10px; transition: all 0.3s; }");
        index.append(".btn:hover { background: #005a9c; transform: translateY(-2px); box-shadow: 0 5px 15px rgba(0,0,0,0.2); }");
        index.append(".btn-success { background: #28a745; } .btn-success:hover { background: #218838; }");
        index.append(".btn-info { background: #17a2b8; } .btn-info:hover { background: #138496; }");
        index.append("h1 { margin: 0; font-size: 2.5em; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }");
        index.append("h2 { color: #333; border-bottom: 2px solid #007acc; padding-bottom: 10px; }");
        index.append(".individual-test { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #007acc; }");
        index.append("</style></head><body>");
        
        index.append("<div class='header'>");
        index.append("<h1>🚀 Test Session Report</h1>");
        index.append("<p>Session: ").append(sessionTimestamp).append(" | Tests Executed: ").append(allResults.size()).append("</p>");
        index.append("</div>");
        
        index.append("<div class='container'>");
        
        // Overall metrics
        long totalSamples = allResults.stream().mapToLong(e -> e.result.getTotalSamples()).sum();
        long totalErrors = allResults.stream().mapToLong(e -> e.result.getErrorCount()).sum();
        double avgResponseTime = allResults.stream().mapToDouble(e -> e.result.getMeanResponseTime().toMillis()).average().orElse(0.0);
        
        index.append("<div class='card'>");
        index.append("<h2>📊 Session Overview</h2>");
        index.append("<div class='grid'>");
        index.append("<div class='metric-card'><div class='metric-value'>").append(allResults.size()).append("</div><div>Tests Executed</div></div>");
        index.append("<div class='metric-card'><div class='metric-value'>").append(totalSamples).append("</div><div>Total Requests</div></div>");
        index.append("<div class='metric-card'><div class='metric-value'>").append(totalErrors).append("</div><div>Total Errors</div></div>");
        index.append("<div class='metric-card'><div class='metric-value'>").append(String.format("%.0f", avgResponseTime)).append("ms</div><div>Avg Response Time</div></div>");
        index.append("</div>");
        index.append("</div>");
        
        // Quick navigation
        index.append("<div class='card'>");
        index.append("<h2>🎯 Quick Access</h2>");
        index.append("<div style='text-align: center;'>");
        index.append("<a href='consolidated-report/consolidated-summary.html' class='btn btn-success'>📊 Consolidated Report</a>");
        index.append("<a href='consolidated-report/consolidated-results.csv' class='btn btn-info'>📋 Download CSV</a>");
        index.append("<a href='consolidated-report/all-tests-combined.jtl' class='btn'>📈 Raw JTL Data</a>");
        index.append("</div>");
        index.append("</div>");
        
        // Individual test links
        index.append("<div class='card'>");
        index.append("<h2>📂 Individual Test Reports</h2>");
        for (TestResultEntry entry : allResults) {
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            String dirName = safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-");
            
            index.append("<div class='individual-test'>");
            index.append("<h3>").append(entry.testName).append("</h3>");
            index.append("<p><strong>Thread:</strong> ").append(entry.threadName).append(" | ");
            index.append("<strong>Samples:</strong> ").append(entry.result.getTotalSamples()).append(" | ");
            index.append("<strong>Errors:</strong> ").append(entry.result.getErrorCount()).append(" | ");
            index.append("<strong>Mean Time:</strong> ").append(entry.result.getMeanResponseTime().toMillis()).append("ms</p>");
            index.append("<a href='individual-reports/").append(dirName).append("/test-summary.html' class='btn'>📄 View Report</a>");
            index.append("</div>");
        }
        index.append("</div>");
        
        index.append("</div>");
        index.append("</body></html>");
        
        Files.write(consolidatedReportDir.resolve("index.html"), index.toString().getBytes());
    }
    
    private void generateConsolidatedSummary() throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("<!DOCTYPE html><html><head><title>Consolidated Test Report</title>");
        summary.append("<style>");
        summary.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #f8f9fa; }");
        summary.append(".container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 15px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }");
        summary.append("h1 { color: #333; border-bottom: 3px solid #007acc; padding-bottom: 15px; }");
        summary.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        summary.append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        summary.append("th { background: linear-gradient(135deg, #007acc, #005a9c); color: white; font-weight: bold; }");
        summary.append("tr:nth-child(even) { background-color: #f2f2f2; }");
        summary.append("tr:hover { background-color: #e8f4fd; }");
        summary.append(".success { color: #28a745; font-weight: bold; }");
        summary.append(".error { color: #dc3545; font-weight: bold; }");
        summary.append(".summary-card { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 20px; margin: 15px 0; border-radius: 10px; text-align: center; }");
        summary.append(".back-link { display: inline-block; margin: 20px 0; padding: 10px 20px; background: #6c757d; color: white; text-decoration: none; border-radius: 5px; }");
        summary.append("</style></head><body>");
        
        summary.append("<div class='container'>");
        summary.append("<a href='../index.html' class='back-link'>← Back to Session Index</a>");
        summary.append("<h1>📊 Consolidated Performance Test Report</h1>");
        summary.append("<p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>");
        summary.append("<p><strong>Total Tests:</strong> ").append(allResults.size()).append("</p>");
        
        // Overall summary
        long totalSamples = allResults.stream().mapToLong(e -> e.result.getTotalSamples()).sum();
        long totalErrors = allResults.stream().mapToLong(e -> e.result.getErrorCount()).sum();
        double avgResponseTime = allResults.stream().mapToDouble(e -> e.result.getMeanResponseTime().toMillis()).average().orElse(0.0);
        
        summary.append("<div class='summary-card'>");
        summary.append("<h2>🎯 Overall Summary</h2>");
        summary.append("<div style='display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-top: 15px;'>");
        summary.append("<div><div style='font-size: 2em; margin-bottom: 5px;'>").append(totalSamples).append("</div><div>Total Samples</div></div>");
        summary.append("<div><div style='font-size: 2em; margin-bottom: 5px;'>").append(totalErrors).append("</div><div>Total Errors</div></div>");
        summary.append("<div><div style='font-size: 2em; margin-bottom: 5px;'>").append(String.format("%.2f%%", totalSamples > 0 ? (double) totalErrors / totalSamples * 100 : 0)).append("</div><div>Error Rate</div></div>");
        summary.append("<div><div style='font-size: 2em; margin-bottom: 5px;'>").append(String.format("%.0f", avgResponseTime)).append("ms</div><div>Avg Response</div></div>");
        summary.append("</div></div>");
        
        // Individual test results table
        summary.append("<h2>📋 Individual Test Results</h2>");
        summary.append("<table>");
        summary.append("<tr><th>Test Name</th><th>Thread</th><th>Execution Time</th><th>Samples</th><th>Errors</th><th>Error Rate</th><th>Mean Response Time</th><th>Status</th><th>Individual Report</th></tr>");
        
        for (TestResultEntry entry : allResults) {
            String status = entry.result.getErrorCount() == 0 ? "✅ PASSED" : "❌ FAILED";
            String statusClass = entry.result.getErrorCount() == 0 ? "success" : "error";
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            String dirName = safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-");
            
            summary.append("<tr>");
            summary.append("<td>").append(entry.testName).append("</td>");
            summary.append("<td>").append(entry.threadName).append("</td>");
            summary.append("<td>").append(entry.executionTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</td>");
            summary.append("<td>").append(entry.result.getTotalSamples()).append("</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(entry.result.getErrorCount()).append("</span></td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(String.format("%.2f%%", entry.result.getErrorPercentage())).append("</span></td>");
            summary.append("<td>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(status).append("</span></td>");
            summary.append("<td><a href='../individual-reports/").append(dirName).append("/test-summary.html'>📄 View</a></td>");
            summary.append("</tr>");
        }
        
        summary.append("</table>");
        summary.append("<hr><p><i>Consolidated report generated by JMeter DSL Framework</i></p>");
        summary.append("</div></body></html>");
        
        Files.write(consolidatedReportDir.resolve("consolidated-report").resolve("consolidated-summary.html"), summary.toString().getBytes());
    }
    
    private void generateConsolidatedCsv() throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("Test Name,Thread Name,Execution Time,Total Samples,Error Count,Error Rate %,Mean Response Time (ms),Min Response Time (ms),Max Response Time (ms),Median Response Time (ms),Status\n");
        
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
            csv.append(entry.result.getMedianResponseTime().toMillis()).append(",");
            csv.append(entry.result.getErrorCount() == 0 ? "PASSED" : "FAILED").append("\n");
        }
        
        Files.write(consolidatedReportDir.resolve("consolidated-report").resolve("consolidated-results.csv"), csv.toString().getBytes());
    }
    
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path targetPath = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(path, targetPath);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    public Path getConsolidatedReportDir() {
        return consolidatedReportDir;
    }
    
    public String getSessionTimestamp() {
        return sessionTimestamp;
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