package com.project.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.perf.builder.PerformanceTestBuilder;
import org.perf.core.TestConfiguration;
import org.perf.model.ExecutionResult;
import org.perf.utils.FileUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Execution(ExecutionMode.CONCURRENT) // Enable parallel execution for this class
public class ParallelJmeterDslFrameworkTest {
    
    private static final AtomicInteger testCounter = new AtomicInteger(0);
    private TestConfiguration config;
    
    @BeforeEach
    void setUp() throws Exception {
        // Create unique test name for each parallel execution
        int testId = testCounter.incrementAndGet();
        String testName = "ParallelTest-" + testId + "-" + Thread.currentThread().getName();
        
        config = TestConfiguration.builder()
            .testName(testName)
            .baseUrl("https://httpbin.org")
            .resultsDirectory(FileUtils.createResultsDir("parallel-results-" + testId))
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(30))
            .generateHtmlReport(true)
            .build();
        
        System.out.println("🧵 [" + Thread.currentThread().getName() + "] Setting up: " + testName);
    }

    @Test
    @DisplayName("🚀 Parallel Simple GET Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void parallelSimpleGetTest() throws Exception {
        String threadName = Thread.currentThread().getName();
        System.out.println("🧵 [" + threadName + "] Starting Simple GET Test");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(3) // Slightly different load per test
            .withIterations(2)
            .withRampUp(Duration.ofSeconds(1))
            .addRequest()
                .name("Parallel GET Request")
                .get("/get?test=simple&thread=" + threadName)
                .header("Accept", "application/json")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(300))
            .and()
            .execute();
        
        System.out.println("🧵 [" + threadName + "] Simple GET - Samples: " + result.getTotalSamples() + 
                          ", Errors: " + result.getErrorCount() + 
                          ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(6);
        
        System.out.println("✅ [" + threadName + "] Simple GET Test COMPLETED");
    }
    
    @Test
    @DisplayName("🔄 Parallel Workflow Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void parallelWorkflowTest() throws Exception {
        String threadName = Thread.currentThread().getName();
        System.out.println("🧵 [" + threadName + "] Starting Workflow Test");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(3)
            .withRampUp(Duration.ofSeconds(2))
            .addRequest()
                .name("Workflow Step 1")
                .get("/get?workflow=step1&thread=" + threadName)
                .header("Accept", "application/json")
                .thinkTime(Duration.ofMillis(200))
            .and()
            .addRequest()
                .name("Workflow Step 2")
                .post("/post", "{\"workflow\":\"step2\",\"thread\":\"" + threadName + "\"}")
                .header("Content-Type", "application/json")
                .thinkTime(Duration.ofMillis(400))
            .and()
            .addRequest()
                .name("Workflow Step 3")
                .get("/status/200?workflow=step3")
                .header("Thread-ID", threadName)
            .and()
            .execute();
        
        System.out.println("🧵 [" + threadName + "] Workflow - Samples: " + result.getTotalSamples() + 
                          ", Errors: " + result.getErrorCount() + 
                          ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(18);
        
        System.out.println("✅ [" + threadName + "] Workflow Test COMPLETED");
    }
    
    @Test
    @DisplayName("⚡ Parallel Load Test")
    @Execution(ExecutionMode.CONCURRENT)
    public void parallelLoadTest() throws Exception {
        String threadName = Thread.currentThread().getName();
        System.out.println("🧵 [" + threadName + "] Starting Load Test");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(5)
            .withIterations(4)
            .withRampUp(Duration.ofSeconds(3))
            .addRequest()
                .name("Load Test Request")
                .get("/get?load=test&thread=" + threadName)
                .header("Accept", "application/json")
                .header("Load-Test", "parallel")
                .header("Thread-ID", threadName)
                .thinkTime(Duration.ofMillis(150))
            .and()
            .execute();
        
        System.out.println("🧵 [" + threadName + "] Load Test - Samples: " + result.getTotalSamples() + 
                          ", Errors: " + result.getErrorCount() + 
                          ", Error Rate: " + String.format("%.2f%%", result.getErrorPercentage()) +
                          ", Mean: " + result.getMeanResponseTime().toMillis() + "ms");
        
        assertThat(result.getErrorPercentage()).isLessThan(10.0); // Allow higher error rate under parallel load
        assertThat(result.getTotalSamples()).isEqualTo(20);
        
        System.out.println("✅ [" + threadName + "] Load Test COMPLETED");
    }
}