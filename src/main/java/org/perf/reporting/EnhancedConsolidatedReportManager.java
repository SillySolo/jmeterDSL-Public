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

public class EnhancedConsolidatedReportManager {
    private static final EnhancedConsolidatedReportManager INSTANCE = new EnhancedConsolidatedReportManager();
    private final List<TestResultEntry> allResults = new CopyOnWriteArrayList<>();
    private final ReentrantLock reportLock = new ReentrantLock();
    private Path consolidatedReportDir;
    private Path consolidatedJtlFile;
    private final String sessionTimestamp;
    
    private EnhancedConsolidatedReportManager() {
        sessionTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        initializeReportDirectory();
    }
    
    public static EnhancedConsolidatedReportManager getInstance() {
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
        
        copyIndividualReport(entry);
        appendToConsolidatedJtl(entry);
        
        System.out.println("📈 Added result for: " + testName + " | Reports: ✅ | Total tests: " + allResults.size());
    }
    
    private void copyIndividualReport(TestResultEntry entry) {
        try {
            Path sourceDir = entry.result.getConfig().getResultsDirectory();
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            Path targetDir = consolidatedReportDir.resolve("individual-reports")
                .resolve(safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-"));
            
            if (Files.exists(sourceDir)) {
                copyDirectory(sourceDir, targetDir);

                //Add delay for Jtl file to be completly written
                Path jtlFile = targetDir.resolve("results.jtl");
                if(Files.exists(jtlFile)) {
                    // wait for file to be flushed
                    try {
                        Thread.sleep(2000);    
                    } catch (
                     InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    try {
                            // Re-check the file after waiting
                            long fileSize = Files.size(jtlFile);
                            List<String> lines = Files.readAllLines(jtlFile);
                            System.out.println("JTL file size: " + fileSize + " bytes, lines: " + lines.size());
                    } catch (Exception e) {
                        System.err.println("Warning: Could not verify JTL file: " + e.getMessage());
                    }
                }

                createEnhancedIndividualTestSummary(entry, targetDir);
                
                // NEW: Generate individual HTML report post-execution (safe from conflicts)
                generateIndividualHtmlReport(entry, targetDir);
                
                System.out.println("📄 Enhanced individual report created: " + targetDir.getFileName());
            }
        } catch (IOException e) {
            System.err.println("Failed to copy individual report for " + entry.testName + ": " + e.getMessage());
        }
    }
    
    // NEW METHOD: Generate individual HTML report post-execution
    private void generateIndividualHtmlReport(TestResultEntry entry, Path targetDir) {
        try {
            Path jtlFile = targetDir.resolve("results.jtl");
            if (Files.exists(jtlFile) && Files.size(jtlFile) > 100) { // Check if JTL has content
                Path htmlReportDir = targetDir.resolve("html-report");
                Files.createDirectories(htmlReportDir);
                
                System.out.println("📊 [" + entry.threadName + "] Generating HTML report for: " + entry.testName);
                
                // Use JMeter command line tool to generate HTML report (if available)
                generateHtmlReportFromJtl(jtlFile, htmlReportDir);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not generate HTML report for " + entry.testName + ": " + e.getMessage());
            // This is non-critical, continue without HTML report
        }
    }
    
    // NEW METHOD: Generate HTML report from JTL using external JMeter CLI
    private void generateHtmlReportFromJtl(Path jtlFile, Path htmlReportDir) {
        try {
            // Try to find JMeter installation
            String[] possibleJMeterPaths = {
                System.getenv("JMETER_HOME"),
                "/usr/local/bin/jmeter",
                "/opt/jmeter/bin/jmeter",
                "C:\\apache-jmeter\\bin\\jmeter.bat"
            };
            
            String jmeterPath = null;
            for (String path : possibleJMeterPaths) {
                if (path != null && (Files.exists(Paths.get(path)) || Files.exists(Paths.get(path + "/bin/jmeter")) || Files.exists(Paths.get(path + "\\bin\\jmeter.bat")))) {
                    jmeterPath = path.endsWith("jmeter") ? path : path + (System.getProperty("os.name").toLowerCase().contains("windows") ? "\\bin\\jmeter.bat" : "/bin/jmeter");
                    break;
                }
            }
            
            if (jmeterPath != null && Files.exists(Paths.get(jmeterPath))) {
                ProcessBuilder pb = new ProcessBuilder(
                    jmeterPath,
                    "-g", jtlFile.toString(),
                    "-o", htmlReportDir.toString()
                );
                
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    System.out.println("✅ HTML report generated successfully");
                } else {
                    System.out.println("⚠️ JMeter HTML report generation failed (exit code: " + exitCode + ")");
                }
            } else {
                System.out.println("ℹ️ JMeter CLI not found, skipping HTML report generation");
                createPlaceholderHtmlReport(htmlReportDir);
            }
        } catch (Exception e) {
            System.err.println("⚠️ HTML report generation error: " + e.getMessage());
            createPlaceholderHtmlReport(htmlReportDir);
        }
    }
    
    // NEW METHOD: Create placeholder HTML report when JMeter CLI is unavailable
    private void createPlaceholderHtmlReport(Path htmlReportDir) {
        try {
            Files.createDirectories(htmlReportDir);
            String placeholder = "<!DOCTYPE html><html><head><title>HTML Report Unavailable</title></head><body>" +
                "<h1>HTML Report Generation Skipped</h1>" +
                "<p>JMeter CLI not available for HTML report generation.</p>" +
                "<p>JTL file contains all raw data for manual analysis.</p>" +
                "<p><a href='../results.jtl'>View Raw JTL Results</a></p>" +
                "</body></html>";
            Files.write(htmlReportDir.resolve("index.html"), placeholder.getBytes());
        } catch (IOException e) {
            System.err.println("Could not create placeholder HTML report: " + e.getMessage());
        }
    }
    
    // MODIFIED: Enhanced individual test summary with parallel execution note
    private void createEnhancedIndividualTestSummary(TestResultEntry entry, Path reportDir) throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("<!DOCTYPE html><html><head><title>").append(entry.testName).append(" - Individual Report</title>");
        summary.append("<!DOCTYPE html><html><head><title>").append(entry.testName).append(" - Individual Report</title>");
        summary.append("<style>");
        summary.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #f5f7fa; }");
        summary.append(".container { max-width: 1400px; margin: 0 auto; background: white; padding: 30px; border-radius: 15px; box-shadow: 0 5px 20px rgba(0,0,0,0.1); }");
        summary.append("h1 { color: #2c3e50; border-bottom: 4px solid #3498db; padding-bottom: 15px; margin-bottom: 25px; }");
        summary.append("h2 { color: #34495e; border-bottom: 2px solid #bdc3c7; padding-bottom: 10px; margin-top: 40px; }");
        summary.append(".metric-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; margin: 10px 5px; border-radius: 12px; text-align: center; }");
        summary.append(".metric-value { font-size: 28px; font-weight: bold; margin-bottom: 5px; }");
        summary.append(".metric-label { font-size: 14px; opacity: 0.9; }");
        summary.append(".success { color: #27ae60; font-weight: bold; }");
        summary.append(".error { color: #e74c3c; font-weight: bold; }");
        summary.append(".warning { color: #f39c12; font-weight: bold; }");
        summary.append("table { width: 100%; border-collapse: collapse; margin: 25px 0; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }");
        summary.append("th { background: linear-gradient(135deg, #3498db, #2980b9); color: white; padding: 15px 10px; text-align: left; font-weight: 600; }");
        summary.append("td { padding: 12px 10px; border-bottom: 1px solid #ecf0f1; }");
        summary.append("tbody tr:nth-child(even) { background-color: #f8f9fa; }");
        summary.append("tbody tr:hover { background-color: #e8f4fd; }");
        summary.append(".total-row { background: linear-gradient(135deg, #2c3e50, #34495e) !important; color: white; font-weight: bold; }");
        summary.append(".total-row td { border-bottom: none; }");
        summary.append(".numeric { text-align: right; font-family: 'Courier New', monospace; }");
        summary.append(".back-link { display: inline-block; margin-bottom: 20px; padding: 10px 20px; background: #95a5a6; color: white; text-decoration: none; border-radius: 25px; transition: all 0.3s; }");
        summary.append(".back-link:hover { background: #7f8c8d; transform: translateY(-2px); }");
        // NEW: Info box styling for parallel execution note
        summary.append(".info-box { background: #e8f6ff; border-left: 4px solid #3498db; padding: 15px; margin: 20px 0; border-radius: 0 8px 8px 0; }");
        summary.append("</style></head><body>");
        
        summary.append("<div class='container'>");
        summary.append("<a href='../consolidated-report/consolidated-summary.html' class='back-link'>← Back to Consolidated Report</a>");
        summary.append("<h1>🎯 ").append(entry.testName).append(" - Detailed Analysis</h1>");
        
        // NEW: Parallel execution note
        summary.append("<div class='info-box'>");
        summary.append("<p><strong>ℹ️ Parallel Execution Note:</strong> HTML dashboard generation was moved to post-execution phase to avoid conflicts during parallel test execution. All raw data is available in the JTL file for analysis.</p>");
        summary.append("</div>");
        
        // Test metadata (keeping your existing structure)
        summary.append("<table>");
        summary.append("<tr><th colspan='2'>Test Configuration</th></tr>");
        summary.append("<tr><td><strong>Thread Name</strong></td><td>").append(entry.threadName).append("</td></tr>");
        summary.append("<tr><td><strong>Execution Time</strong></td><td>").append(entry.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</td></tr>");
        summary.append("<tr><td><strong>Base URL</strong></td><td>").append(entry.result.getConfig().getBaseUrl()).append("</td></tr>");
        summary.append("<tr><td><strong>Connection Timeout</strong></td><td>").append(entry.result.getConfig().getConnectionTimeout().toSeconds()).append("s</td></tr>");
        summary.append("<tr><td><strong>Response Timeout</strong></td><td>").append(entry.result.getConfig().getResponseTimeout().toSeconds()).append("s</td></tr>");
        summary.append("</table>");
        
        // Key metrics with cards (keeping your existing structure)
        summary.append("<h2>📊 Key Metrics</h2>");
        summary.append("<div style='display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 15px; margin: 20px 0;'>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value'>").append(entry.result.getTotalSamples()).append("</div>");
        summary.append("<div class='metric-label'>Total Samples</div>");
        summary.append("</div>");
        
        summary.append("<div class='metric-card'>");
        summary.append("<div class='metric-value ").append(entry.result.getErrorCount() == 0 ? "success" : "error").append("'>").append(entry.result.getErrorCount()).append("</div>");
        summary.append("<div class='metric-label'>Total Errors</div>");
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
        
        // Response time analysis (keeping your existing structure)
        summary.append("<h2>⏱️ Response Time Analysis</h2>");
        summary.append("<table>");
        summary.append("<tr><th>Metric</th><th class='numeric'>Value (ms)</th></tr>");
        summary.append("<tr><td>Minimum Response Time</td><td class='numeric'>").append(entry.result.getMinResponseTime().toMillis()).append("</td></tr>");
        summary.append("<tr><td>Mean Response Time</td><td class='numeric'>").append(entry.result.getMeanResponseTime().toMillis()).append("</td></tr>");
        summary.append("<tr><td>Median Response Time</td><td class='numeric'>").append(entry.result.getMedianResponseTime().toMillis()).append("</td></tr>");
        summary.append("<tr><td>Maximum Response Time</td><td class='numeric'>").append(entry.result.getMaxResponseTime().toMillis()).append("</td></tr>");
        summary.append("</table>");
        
        // Aggregate Report Section (keeping your existing structure)
        summary.append("<h2>📈 Aggregate Report</h2>");
        summary.append("<p><i>Detailed statistics for each sampler (similar to JMeter's Aggregate Report listener)</i></p>");
        
        // try {
        //     Path jtlFile = reportDir.resolve("results.jtl");
        //     List<AggregateReportParser.SamplerStats> aggregateStats = AggregateReportParser.parseJtlFile(jtlFile);
            
        //     if (!aggregateStats.isEmpty()) {
        //         summary.append("<div style='overflow-x: auto;'>");
        //         summary.append("<table>");
        //         summary.append("<tr>");
        //         summary.append("<th>Sampler</th>");
        //         summary.append("<th class='numeric'>Samples</th>");
        //         summary.append("<th class='numeric'>Average (ms)</th>");
        //         summary.append("<th class='numeric'>Median (ms)</th>");
        //         summary.append("<th class='numeric'>90% Line (ms)</th>");
        //         summary.append("<th class='numeric'>95% Line (ms)</th>");
        //         summary.append("<th class='numeric'>99% Line (ms)</th>");
        //         summary.append("<th class='numeric'>Min (ms)</th>");
        //         summary.append("<th class='numeric'>Max (ms)</th>");
        //         summary.append("<th class='numeric'>Error %</th>");
        //         summary.append("<th class='numeric'>Throughput (/sec)</th>");
        //         summary.append("<th class='numeric'>Received KB/sec</th>");
        //         summary.append("<th class='numeric'>Sent KB/sec</th>");
        //         summary.append("</tr>");
                
        //         for (AggregateReportParser.SamplerStats stats : aggregateStats) {
        //             String rowClass = "TOTAL".equals(stats.getLabel()) ? "total-row" : "";
        //             summary.append("<tr class='").append(rowClass).append("'>");
        //             summary.append("<td><strong>").append(stats.getLabel()).append("</strong></td>");
        //             summary.append("<td class='numeric'>").append(stats.getSamples()).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getAverage())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMedian())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile90())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile95())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile99())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMin())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMax())).append("</td>");
                    
        //             String errorClass = stats.getErrorPercentage() == 0 ? "success" : "error";
        //             summary.append("<td class='numeric ").append(errorClass).append("'>").append(String.format("%.2f%%", stats.getErrorPercentage())).append("</td>");
                    
        //             summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getThroughput())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getReceivedKBPerSec())).append("</td>");
        //             summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getSentKBPerSec())).append("</td>");
        //             summary.append("</tr>");
        //         }
                
        //         summary.append("</table>");
        //         summary.append("</div>");
        //     } else {
        //         summary.append("<p><i>No detailed sampler data available. This may occur if the JTL file is empty or has formatting issues.</i></p>");
        //     }
        // } catch (Exception e) {
        //     summary.append("<p class='error'><i>Error parsing aggregate data: ").append(e.getMessage()).append("</i></p>");
        // }

        try {
            Path jtlFile = reportDir.resolve("results.jtl");
            
            // Better debugging and error handling
            if (!Files.exists(jtlFile)) {
                summary.append("<p class='warning'><i>JTL file not found: ").append(jtlFile.toString()).append("</i></p>");
            } else if (Files.size(jtlFile) == 0) {
                summary.append("<p class='warning'><i>JTL file is empty</i></p>");
            } else {
                summary.append("<p><strong>JTL File:</strong> ").append(jtlFile.getFileName()).append(" (").append(Files.size(jtlFile)).append(" bytes)</p>");
                
                List<AggregateReportParser.SamplerStats> aggregateStats = AggregateReportParser.parseJtlFile(jtlFile);
                
                if (!aggregateStats.isEmpty()) {
                    summary.append("<div style='overflow-x: auto;'>");
                    summary.append("<table>");
                    summary.append("<tr>");
                    summary.append("<th>Sampler</th>");
                    summary.append("<th class='numeric'>Samples</th>");
                    summary.append("<th class='numeric'>Average (ms)</th>");
                    summary.append("<th class='numeric'>Median (ms)</th>");
                    summary.append("<th class='numeric'>90% Line (ms)</th>");
                    summary.append("<th class='numeric'>95% Line (ms)</th>");
                    summary.append("<th class='numeric'>99% Line (ms)</th>");
                    summary.append("<th class='numeric'>Min (ms)</th>");
                    summary.append("<th class='numeric'>Max (ms)</th>");
                    summary.append("<th class='numeric'>Error %</th>");
                    summary.append("<th class='numeric'>Throughput (/sec)</th>");
                    summary.append("<th class='numeric'>Received KB/sec</th>");
                    summary.append("<th class='numeric'>Sent KB/sec</th>");
                    summary.append("</tr>");
                    
                    for (AggregateReportParser.SamplerStats stats : aggregateStats) {
                        String rowClass = "TOTAL".equals(stats.getLabel()) ? "total-row" : "";
                        summary.append("<tr class='").append(rowClass).append("'>");
                        summary.append("<td><strong>").append(stats.getLabel()).append("</strong></td>");
                        summary.append("<td class='numeric'>").append(stats.getSamples()).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getAverage())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMedian())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile90())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile95())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getPercentile99())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMin())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.0f", stats.getMax())).append("</td>");
                        
                        String errorClass = stats.getErrorPercentage() == 0 ? "success" : "error";
                        summary.append("<td class='numeric ").append(errorClass).append("'>").append(String.format("%.2f%%", stats.getErrorPercentage())).append("</td>");
                        
                        summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getThroughput())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getReceivedKBPerSec())).append("</td>");
                        summary.append("<td class='numeric'>").append(String.format("%.2f", stats.getSentKBPerSec())).append("</td>");
                        summary.append("</tr>");
                    }
                    
                    summary.append("</table>");
                    summary.append("</div>");
                } else {
                    summary.append("<p class='warning'><i>No sampler statistics found in JTL file. The file may be in an unexpected format or empty.</i></p>");
                    
                    // Add JTL file preview for debugging
                    List<String> firstFewLines = Files.readAllLines(jtlFile).stream().limit(5).collect(java.util.stream.Collectors.toList());
                    summary.append("<details><summary>JTL File Preview (first 5 lines)</summary><pre>");
                    for (String line : firstFewLines) {
                        summary.append(line).append("\n");
                    }
                    summary.append("</pre></details>");
                }
            }
        } catch (Exception e) {
                summary.append("<p class='error'><i>Error parsing aggregate data: ").append(e.getMessage()).append("</i></p>");
                summary.append("<p><strong>Debug Info:</strong> Error Type: ").append(e.getClass().getSimpleName()).append("</p>");
                
                // Add stack trace for debugging
                summary.append("<details><summary>Technical Details</summary><pre>");
                summary.append("Exception: ").append(e.toString()).append("\n");
                if (e.getCause() != null) {
                    summary.append("Cause: ").append(e.getCause().toString()).append("\n");
                }
                summary.append("</pre></details>");
            }
        
        // Links to other reports (keeping your existing structure)
        summary.append("<h2>🔗 Additional Reports</h2>");
        summary.append("<ul>");
        if (Files.exists(reportDir.resolve("results.jtl"))) {
            summary.append("<li><a href='results.jtl'>📊 Raw JTL Results Data</a></li>");
        }
        if (Files.exists(reportDir.resolve("html-report").resolve("index.html"))) {
            summary.append("<li><a href='html-report/index.html'>📈 JMeter HTML Dashboard</a> ").append("(Generated post-execution)</li>");
        }
        summary.append("<li><a href='../consolidated-report/consolidated-summary.html'>📋 Consolidated Report</a></li>");
        summary.append("<li><a href='../index.html'>🏠 Session Index</a></li>");
        summary.append("</ul>");
        
        summary.append("<hr><p style='text-align: center; color: #7f8c8d;'><i>Enhanced individual report with aggregate analysis - Generated by JMeter DSL Framework</i></p>");
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
                System.err.println("Failed to generate consolidated report: " + e.getMessage());
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("🎉 ENHANCED DUAL REPORTING COMPLETE!");
            System.out.println("=".repeat(100));
            System.out.println("📁 Session Directory: " + consolidatedReportDir);
            System.out.println("🏠 Main Index: " + consolidatedReportDir.resolve("index.html"));
            System.out.println("📊 Consolidated Summary: " + consolidatedReportDir.resolve("consolidated-report/consolidated-summary.html"));
            System.out.println("📂 Individual Reports with Aggregate Analysis: " + consolidatedReportDir.resolve("individual-reports"));
            // MODIFIED: Updated final message about HTML generation
            System.out.println("ℹ️ Note: HTML dashboards generated post-execution to avoid parallel conflicts");
            System.out.println("=".repeat(100));
            
        } finally {
            reportLock.unlock();
        }
    }
    
    // Keep all your existing methods unchanged
    private void generateSessionIndex() throws IOException {
        // Your existing implementation stays exactly the same
        // Just keeping the structure as-is
        StringBuilder index = new StringBuilder();
        index.append("<!DOCTYPE html><html><head><title>Test Session Report - ").append(sessionTimestamp).append("</title>");
        index.append("<style>");
        index.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }");
        index.append(".header { background: rgba(255,255,255,0.1); padding: 40px; text-align: center; color: white; }");
        index.append(".container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }");
        index.append(".card { background: white; border-radius: 15px; padding: 30px; margin: 20px 0; box-shadow: 0 8px 32px rgba(0,0,0,0.1); }");
        index.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }");
        index.append(".metric-card { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; text-align: center; padding: 25px; border-radius: 15px; }");
        index.append(".metric-value { font-size: 2.8em; font-weight: bold; margin-bottom: 10px; }");
        index.append(".btn { display: inline-block; padding: 15px 30px; background: #3498db; color: white; text-decoration: none; border-radius: 30px; margin: 10px; transition: all 0.3s; font-weight: 600; }");
        index.append(".btn:hover { background: #2980b9; transform: translateY(-3px); box-shadow: 0 8px 25px rgba(0,0,0,0.2); }");
        index.append(".btn-success { background: #27ae60; } .btn-success:hover { background: #229954; }");
        index.append(".btn-info { background: #17a2b8; } .btn-info:hover { background: #138496; }");
        index.append(".btn-secondary { background: #6c757d; } .btn-secondary:hover { background: #5a6268; }");
        index.append("h1 { margin: 0; font-size: 3em; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }");
        index.append("h2 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; }");
        index.append(".individual-test { background: #f8f9fa; padding: 20px; margin: 15px 0; border-radius: 12px; border-left: 5px solid #3498db; transition: all 0.3s; }");
        index.append(".individual-test:hover { box-shadow: 0 5px 15px rgba(0,0,0,0.1); transform: translateY(-2px); }");
        index.append(".test-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; margin: 10px 0; }");
        index.append(".stat { text-align: center; padding: 8px; background: white; border-radius: 8px; }");
        index.append(".stat-value { font-weight: bold; font-size: 1.1em; color: #2c3e50; }");
        index.append(".stat-label { font-size: 0.85em; color: #7f8c8d; }");
        index.append("</style></head><body>");
        
        index.append("<div class='header'>");
        index.append("<h1>🚀 Enhanced Test Session Report</h1>");
        index.append("<p style='font-size: 1.2em; margin-top: 15px;'>Session: ").append(sessionTimestamp).append(" | Tests Executed: ").append(allResults.size()).append(" | Individual Reports with Aggregate Analysis</p>");
        index.append("</div>");
        
        index.append("<div class='container'>");
        
        // Overall metrics (keeping your existing calculation logic)
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
        
        // Quick navigation (keeping your existing structure)
        index.append("<div class='card'>");
        index.append("<h2>🎯 Quick Access</h2>");
        index.append("<div style='text-align: center;'>");
        index.append("<a href='consolidated-report/consolidated-summary.html' class='btn btn-success'>📊 Consolidated Report</a>");
        index.append("<a href='consolidated-report/consolidated-results.csv' class='btn btn-info'>📋 Download CSV</a>");
        index.append("<a href='consolidated-report/all-tests-combined.jtl' class='btn btn-secondary'>📈 Raw JTL Data</a>");
        index.append("</div>");
        index.append("</div>");
        
        // Individual test links with enhanced details (keeping your existing structure)
        index.append("<div class='card'>");
        index.append("<h2>📂 Individual Test Reports with Aggregate Analysis</h2>");
        index.append("<p><i>Each report now includes detailed sampler statistics similar to JMeter's Aggregate Report listener</i></p>");
        
        for (TestResultEntry entry : allResults) {
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            String dirName = safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-");
            
            index.append("<div class='individual-test'>");
            index.append("<h3 style='margin-top: 0; color: #2c3e50;'>").append(entry.testName).append("</h3>");
            index.append("<p><strong>Thread:</strong> ").append(entry.threadName).append(" | <strong>Execution:</strong> ").append(entry.executionTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</p>");
            
            index.append("<div class='test-stats'>");
            index.append("<div class='stat'><div class='stat-value'>").append(entry.result.getTotalSamples()).append("</div><div class='stat-label'>Samples</div></div>");
            index.append("<div class='stat'><div class='stat-value'>").append(entry.result.getErrorCount()).append("</div><div class='stat-label'>Errors</div></div>");
            index.append("<div class='stat'><div class='stat-value'>").append(String.format("%.2f%%", entry.result.getErrorPercentage())).append("</div><div class='stat-label'>Error Rate</div></div>");
            index.append("<div class='stat'><div class='stat-value'>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</div><div class='stat-label'>Mean Time</div></div>");
            index.append("<div class='stat'><div class='stat-value'>").append(entry.result.getMinResponseTime().toMillis()).append("ms</div><div class='stat-label'>Min Time</div></div>");
            index.append("<div class='stat'><div class='stat-value'>").append(entry.result.getMaxResponseTime().toMillis()).append("ms</div><div class='stat-label'>Max Time</div></div>");
            index.append("</div>");
            
            index.append("<a href='individual-reports/").append(dirName).append("/test-summary.html' class='btn'>📄 View Enhanced Report with Aggregate Analysis</a>");
            index.append("</div>");
        }
        index.append("</div>");
        
        index.append("</div>");
        index.append("</body></html>");
        
        Files.write(consolidatedReportDir.resolve("index.html"), index.toString().getBytes());
    }
    
    // Keep all your existing methods exactly as they are
    private void generateConsolidatedSummary() throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("<!DOCTYPE html><html><head><title>Consolidated Test Report</title>");
        summary.append("<style>");
        summary.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #f8f9fa; }");
        summary.append(".container { max-width: 1400px; margin: 0 auto; background: white; padding: 30px; border-radius: 15px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }");
        summary.append("h1 { color: #2c3e50; border-bottom: 4px solid #3498db; padding-bottom: 15px; }");
        summary.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        summary.append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        summary.append("th { background: linear-gradient(135deg, #3498db, #2980b9); color: white; font-weight: bold; }");
        summary.append("tr:nth-child(even) { background-color: #f2f2f2; }");
        summary.append("tr:hover { background-color: #e8f4fd; }");
        summary.append(".success { color: #27ae60; font-weight: bold; }");
        summary.append(".error { color: #e74c3c; font-weight: bold; }");
        summary.append(".summary-card { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 25px; margin: 20px 0; border-radius: 15px; text-align: center; }");
        summary.append(".back-link { display: inline-block; margin: 20px 0; padding: 12px 25px; background: #6c757d; color: white; text-decoration: none; border-radius: 25px; transition: all 0.3s; }");
        summary.append(".back-link:hover { background: #5a6268; transform: translateY(-2px); }");
        summary.append("</style></head><body>");
        
        summary.append("<div class='container'>");
        summary.append("<a href='../index.html' class='back-link'>← Back to Session Index</a>");
        summary.append("<h1>📊 Consolidated Performance Test Report</h1>");
        summary.append("<p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>");
        summary.append("<p><strong>Total Tests:</strong> ").append(allResults.size()).append(" | <strong>Enhancement:</strong> Individual reports now include detailed aggregate analysis</p>");
        
        // Overall summary (keeping your existing calculation logic)
        long totalSamples = allResults.stream().mapToLong(e -> e.result.getTotalSamples()).sum();
        long totalErrors = allResults.stream().mapToLong(e -> e.result.getErrorCount()).sum();
        double avgResponseTime = allResults.stream().mapToDouble(e -> e.result.getMeanResponseTime().toMillis()).average().orElse(0.0);
        
        summary.append("<div class='summary-card'>");
        summary.append("<h2>🎯 Overall Summary</h2>");
        summary.append("<div style='display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-top: 20px;'>");
        summary.append("<div><div style='font-size: 2.5em; margin-bottom: 8px;'>").append(totalSamples).append("</div><div>Total Samples</div></div>");
        summary.append("<div><div style='font-size: 2.5em; margin-bottom: 8px;'>").append(totalErrors).append("</div><div>Total Errors</div></div>");
        summary.append("<div><div style='font-size: 2.5em; margin-bottom: 8px;'>").append(String.format("%.2f%%", totalSamples > 0 ? (double) totalErrors / totalSamples * 100 : 0)).append("</div><div>Error Rate</div></div>");
        summary.append("<div><div style='font-size: 2.5em; margin-bottom: 8px;'>").append(String.format("%.0f", avgResponseTime)).append("ms</div><div>Avg Response</div></div>");
        summary.append("</div></div>");
        
        // Individual test results table (keeping your existing structure)
        summary.append("<h2>📋 Individual Test Results</h2>");
        summary.append("<div style='overflow-x: auto;'>");
        summary.append("<table>");
        summary.append("<tr><th>Test Name</th><th>Thread</th><th>Execution Time</th><th>Samples</th><th>Errors</th><th>Error Rate</th><th>Mean Response Time</th><th>Min Time</th><th>Max Time</th><th>Status</th><th>Enhanced Report</th></tr>");
        
        for (TestResultEntry entry : allResults) {
            String status = entry.result.getErrorCount() == 0 ? "✅ PASSED" : "❌ FAILED";
            String statusClass = entry.result.getErrorCount() == 0 ? "success" : "error";
            String safeTestName = entry.testName.replaceAll("[^a-zA-Z0-9-_]", "-");
            String dirName = safeTestName + "-" + entry.threadName.replaceAll("[^a-zA-Z0-9-_]", "-");
            
            summary.append("<tr>");
            summary.append("<td><strong>").append(entry.testName).append("</strong></td>");
            summary.append("<td>").append(entry.threadName).append("</td>");
            summary.append("<td>").append(entry.executionTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</td>");
            summary.append("<td>").append(entry.result.getTotalSamples()).append("</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(entry.result.getErrorCount()).append("</span></td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(String.format("%.2f%%", entry.result.getErrorPercentage())).append("</span></td>");
            summary.append("<td>").append(entry.result.getMeanResponseTime().toMillis()).append("ms</td>");
            summary.append("<td>").append(entry.result.getMinResponseTime().toMillis()).append("ms</td>");
            summary.append("<td>").append(entry.result.getMaxResponseTime().toMillis()).append("ms</td>");
            summary.append("<td><span class='").append(statusClass).append("'>").append(status).append("</span></td>");
            summary.append("<td><a href='../individual-reports/").append(dirName).append("/test-summary.html'>📈 View Aggregate Analysis</a></td>");
            summary.append("</tr>");
        }
        
        summary.append("</table>");
        summary.append("</div>");
        summary.append("<hr><p style='text-align: center; color: #7f8c8d;'><i>Enhanced consolidated report with individual aggregate analysis - Generated by JMeter DSL Framework</i></p>");
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