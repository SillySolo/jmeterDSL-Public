// ============================================================================
// UPDATED PARALLEL TEST CLASS WITH CONSOLIDATED REPORTING
// ============================================================================

package com.project.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.perf.builder.PerformanceTestBuilder;
import org.perf.core.TestConfiguration;
import org.perf.model.ExecutionResult;
import org.perf.reporting.ConsolidatedReportManager;
import org.perf.utils.FileUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Execution(ExecutionMode.CONCURRENT)
public class ConsolidatedParallelTest {
    
    private static final AtomicInteger testCounter = new AtomicInteger(0);
    private static final ConsolidatedReportManager reportManager = ConsolidatedReportManager.getInstance();
    private TestConfiguration config;
    
    @BeforeEach
    void setUp() throws Exception {
        int testId = testCounter.incrementAndGet();
        String testName = "Test-" + testId;
        
        config = TestConfiguration.builder()
            .testName(testName)
            .baseUrl("https://httpbin.org")
            .resultsDirectory(FileUtils.createResultsDir("individual-" + testId))
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(30))
            .generateHtmlReport(false) // Disable individual HTML reports
            .build();
        
        System.out.println("🧵 [" + Thread.currentThread().getName() + "] Setting up: " + testName);
    }
    
    @AfterAll
    static void generateConsolidatedReport() {
        System.out.println("\n🎯 All tests completed. Generating consolidated report...");
        reportManager.generateConsolidatedReport();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🎉 CONSOLIDATED REPORT READY!");
        System.out.println("📁 Location: " + reportManager.getConsolidatedReportDir());
        System.out.println("📊 Open: " + reportManager.getConsolidatedReportDir().resolve("consolidated-summary.html"));
        System.out.println("📋 CSV: " + reportManager.getConsolidatedReportDir().resolve("consolidated-results.csv"));
        System.out.println("📈 JTL: " + reportManager.getConsolidatedReportDir().resolve("consolidated-results.jtl"));
        System.out.println("=".repeat(80));
    }

    @Test
    @DisplayName("🚀 Simple GET Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void simpleGetTest() throws Exception {
        String testName = "Simple GET Test";
        System.out.println("🧵 [" + Thread.currentThread().getName() + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(3)
            .withIterations(4)
            .withRampUp(Duration.ofSeconds(1))
            .addRequest()
                .name("Simple GET Request")
                .get("/get?test=simple")
                .header("Accept", "application/json")
                .thinkTime(Duration.ofMillis(300))
            .and()
            .execute();
        
        // Add to consolidated report
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(12);
        
        System.out.println("✅ [" + Thread.currentThread().getName() + "] " + testName + " COMPLETED");
    }
    
    @Test
    @DisplayName("🔄 Workflow Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void workflowTest() throws Exception {
        String testName = "Multi-Step Workflow Test";
        System.out.println("🧵 [" + Thread.currentThread().getName() + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(3)
            .withRampUp(Duration.ofSeconds(2))
            .addRequest()
                .name("Workflow Step 1")
                .get("/get?workflow=step1")
                .thinkTime(Duration.ofMillis(200))
            .and()
            .addRequest()
                .name("Workflow Step 2")
                .post("/post", "{\"workflow\":\"step2\"}")
                .thinkTime(Duration.ofMillis(400))
            .and()
            .addRequest()
                .name("Workflow Step 3")
                .get("/status/200")
            .and()
            .execute();
        
        // Add to consolidated report
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(18);
        
        System.out.println("✅ [" + Thread.currentThread().getName() + "] " + testName + " COMPLETED");
    }
    
    @Test
    @DisplayName("⚡ Load Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void loadTest() throws Exception {
        String testName = "High Load Test";
        System.out.println("🧵 [" + Thread.currentThread().getName() + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(5)
            .withIterations(4)
            .withRampUp(Duration.ofSeconds(3))
            .addRequest()
                .name("Load Test Request")
                .get("/get?load=test")
                .header("Load-Test", "true")
                .thinkTime(Duration.ofMillis(150))
            .and()
            .execute();
        
        // Add to consolidated report
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorPercentage()).isLessThan(10.0);
        assertThat(result.getTotalSamples()).isEqualTo(20);
        
        System.out.println("✅ [" + Thread.currentThread().getName() + "] " + testName + " COMPLETED");
    }
}