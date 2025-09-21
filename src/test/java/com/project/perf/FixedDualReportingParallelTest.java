package com.project.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.perf.builder.PerformanceTestBuilder;
import org.perf.core.TestConfiguration;
import org.perf.model.ExecutionResult;
import org.perf.reporting.EnhancedConsolidatedReportManager;
import org.perf.utils.FileUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Execution(ExecutionMode.CONCURRENT)
public class FixedDualReportingParallelTest {
    
    private static final AtomicInteger testCounter = new AtomicInteger(0);
    private static final EnhancedConsolidatedReportManager reportManager = EnhancedConsolidatedReportManager.getInstance();
    private TestConfiguration config;
    
    @BeforeAll
    static void initializeFramework() {
        System.out.println("🚀 Initializing JMeter DSL Framework for parallel execution...");
        System.out.println("ℹ️ HTML report generation disabled during parallel execution to prevent conflicts");
    }
    
    @BeforeEach
    void setUp() throws Exception {
        int testId = testCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        String testName = "Test-" + testId + "-" + threadName.replaceAll("[^a-zA-Z0-9-_]", "-");
        
        config = TestConfiguration.builder()
            .testName(testName)
            .baseUrl("https://httpbin.org")
            .resultsDirectory(FileUtils.createResultsDir("individual-" + testId + "-" + threadName.replaceAll("[^a-zA-Z0-9-_]", "-")))
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(30))
            .generateHtmlReport(false) // FIXED: Disable HTML reports during parallel execution
            .build();
        
        System.out.println("🧵 [" + threadName + "] Setting up: " + testName);
    }
    
    @AfterAll
    static void generateConsolidatedReport() {
        try {
            Thread.sleep(3000); // Ensure all tests complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\n🎯 All tests completed. Generating comprehensive reporting...");
        reportManager.generateConsolidatedReport();
    }

    @Test
    @DisplayName("🚀 Simple GET Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void simpleGetTest() throws Exception {
        String testName = "Simple GET Test";
        String threadName = Thread.currentThread().getName();
        
        System.out.println("🧵 [" + threadName + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(3)
            .withIterations(4)
            .withRampUp(Duration.ofSeconds(1))
            .addRequest()
                .name("Simple GET Request")
                .get("/get?test=simple&thread=" + threadName)
                .header("Accept", "application/json")
                .header("User-Agent", "JMeter-DSL-Framework/1.0")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(300))
            .and()
            .execute();
        
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(12);
        
        System.out.println("✅ [" + threadName + "] " + testName + " COMPLETED");
        System.out.println("   📊 Samples: " + result.getTotalSamples() + ", Errors: " + result.getErrorCount() + ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
    }
    
    @Test
    @DisplayName("🔄 Multi-Step Workflow Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void workflowTest() throws Exception {
        String testName = "Multi-Step Workflow Test";
        String threadName = Thread.currentThread().getName();
        
        System.out.println("🧵 [" + threadName + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(3)
            .withRampUp(Duration.ofSeconds(2))
            .addRequest()
                .name("Workflow Step 1: Authentication")
                .get("/get?workflow=step1&thread=" + threadName)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer test-token")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(200))
            .and()
            .addRequest()
                .name("Workflow Step 2: Create Resource")
                .post("/post", "{\"workflow\":\"step2\",\"thread\":\"" + threadName + "\",\"data\":\"test\"}")
                .header("Content-Type", "application/json")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(400))
            .and()
            .addRequest()
                .name("Workflow Step 3: Verify Creation")
                .get("/status/201?workflow=step3")
                .header("Accept", "application/json")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(100))
            .and()
            .addRequest()
                .name("Workflow Step 4: Cleanup")
                .get("/status/200?cleanup=true")
                .header("Accept", "application/json")
                .header("Thread-ID", threadName)
            .and()
            .execute();
        
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(24);
        
        System.out.println("✅ [" + threadName + "] " + testName + " COMPLETED");
        System.out.println("   📊 Samples: " + result.getTotalSamples() + ", Errors: " + result.getErrorCount() + ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
    }
    
    @Test
    @DisplayName("⚡ High Load Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void loadTest() throws Exception {
        String testName = "High Load Test";
        String threadName = Thread.currentThread().getName();
        
        System.out.println("🧵 [" + threadName + "] Starting " + testName);
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(5)
            .withIterations(4)
            .withRampUp(Duration.ofSeconds(3))
            .addRequest()
                .name("Load Test - Get User List")
                .get("/get?load=test&thread=" + threadName + "&endpoint=users")
                .header("Accept", "application/json")
                .header("Load-Test", "true")
                .header("Cache-Control", "no-cache")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(150))
            .and()
            .addRequest()
                .name("Load Test - Create User")
                .post("/post", "{\"load\":\"test\",\"thread\":\"" + threadName + "\",\"action\":\"create\"}")
                .header("Content-Type", "application/json")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(200))
            .and()
            .execute();
        
        reportManager.addTestResult(testName, result);
        
        assertThat(result.getErrorPercentage())
            .as("Error rate should be acceptable under load")
            .isLessThan(10.0);
            
        assertThat(result.getTotalSamples())
            .as("All load test samples should be attempted")
            .isEqualTo(40);
            
        assertThat(result.getMeanResponseTime())
            .as("Response time should be reasonable under load")
            .isLessThan(Duration.ofSeconds(30));
        
        System.out.println("✅ [" + threadName + "] " + testName + " COMPLETED");
        System.out.println("   📊 Samples: " + result.getTotalSamples() + ", Errors: " + result.getErrorCount() + ", Error Rate: " + String.format("%.2f%%", result.getErrorPercentage()) + ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
    }
}