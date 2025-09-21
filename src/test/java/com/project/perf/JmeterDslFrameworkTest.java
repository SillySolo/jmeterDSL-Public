package com.project.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.perf.builder.PerformanceTestBuilder;
import org.perf.core.TestConfiguration;
import org.perf.model.ExecutionResult;
import org.perf.utils.FileUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

public class JmeterDslFrameworkTest {
    
    private TestConfiguration config;
    
    @BeforeEach
    void setUp() throws Exception {
        config = TestConfiguration.builder()
            .testName("Framework Test")
            .baseUrl("https://httpbin.org")
            .resultsDirectory(FileUtils.createTimestampedResultsDir())
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(30))
            .generateHtmlReport(true)
            .build();
        
        System.out.println("Test setup completed. Results will be saved to: " + config.getResultsDirectory());
    }

    @Test
    @DisplayName("🚀 Simple GET Request Test")
    public void simpleGetTest() throws Exception {
        System.out.println("\n=== Starting Simple GET Test ===");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(3)
            .withRampUp(Duration.ofSeconds(1))
            .addRequest()
                .name("Simple GET Request")
                .get("/get")
                .header("Accept", "application/json")
                .header("User-Agent", "JMeter-DSL-Framework/1.0")
                .thinkTime(Duration.ofMillis(500))
            .and()
            .execute();
        
        // Print results
        System.out.println("=== Test Results ===");
        System.out.println("Total Samples: " + result.getTotalSamples());
        System.out.println("Error Count: " + result.getErrorCount());
        System.out.println("Error Rate: " + String.format("%.2f%%", result.getErrorPercentage()));
        System.out.println("Mean Response Time: " + result.getMeanResponseTime().toMillis() + "ms");
        System.out.println("Min Response Time: " + result.getMinResponseTime().toMillis() + "ms");
        System.out.println("Max Response Time: " + result.getMaxResponseTime().toMillis() + "ms");
        System.out.println("Median Response Time: " + result.getMedianResponseTime().toMillis() + "ms");
        
        // Assertions
        assertThat(result.getErrorCount())
            .as("No errors should occur")
            .isEqualTo(0);
            
        assertThat(result.getTotalSamples())
            .as("All samples should be executed")
            .isEqualTo(6); // 2 threads * 3 iterations
            
        assertThat(result.getMeanResponseTime())
            .as("Response time should be reasonable")
            .isLessThan(Duration.ofSeconds(10));
            
        System.out.println("✅ Simple GET Test PASSED!");
    }
    
    @Test
    @DisplayName("🔄 Multi-Request API Workflow Test")
    public void multiRequestWorkflowTest() throws Exception {
        System.out.println("\n=== Starting Multi-Request Workflow Test ===");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(2)
            .withRampUp(Duration.ofSeconds(2))
            .addRequest()
                .name("Step 1: Get User Info")
                .get("/get")
                .header("Accept", "application/json")
                .thinkTime(Duration.ofMillis(300))
            .and()
            .addRequest()
                .name("Step 2: Create Data")
                .post("/post", "{\"name\":\"TestUser\",\"framework\":\"JMeter-DSL\"}")
                .header("Content-Type", "application/json")
                .thinkTime(Duration.ofMillis(500))
            .and()
            .addRequest()
                .name("Step 3: Verify Status")
                .get("/status/200")
                .header("Accept", "application/json")
                .thinkTime(Duration.ofMillis(200))
            .and()
            .execute();
        
        // Print workflow results
        System.out.println("=== Workflow Test Results ===");
        System.out.println("Total Samples: " + result.getTotalSamples());
        System.out.println("Error Count: " + result.getErrorCount());
        System.out.println("Error Rate: " + String.format("%.2f%%", result.getErrorPercentage()));
        System.out.println("Mean Response Time: " + result.getMeanResponseTime().toMillis() + "ms");
        
        // Assertions
        assertThat(result.getErrorCount())
            .as("No errors should occur in workflow")
            .isEqualTo(0);
            
        assertThat(result.getTotalSamples())
            .as("All workflow steps should be executed")
            .isEqualTo(12); // 2 threads * 2 iterations * 3 requests
            
        assertThat(result.getMeanResponseTime())
            .as("Workflow response time should be reasonable")
            .isLessThan(Duration.ofSeconds(15));
            
        System.out.println("✅ Multi-Request Workflow Test PASSED!");
    }
    
    @Test
    @DisplayName("⚡ High Load Test")
    public void highLoadTest() throws Exception {
        System.out.println("\n=== Starting High Load Test ===");
        
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(10)
            .withIterations(5)
            .withRampUp(Duration.ofSeconds(5))
            .addRequest()
                .name("Load Test Request")
                .get("/get")
                .header("Accept", "application/json")
                .header("Load-Test", "true")
                .thinkTime(Duration.ofMillis(100))
            .and()
            .execute();
        
        // Print load test results
        System.out.println("=== Load Test Results ===");
        System.out.println("Total Samples: " + result.getTotalSamples());
        System.out.println("Error Count: " + result.getErrorCount());
        System.out.println("Error Rate: " + String.format("%.2f%%", result.getErrorPercentage()));
        System.out.println("Mean Response Time: " + result.getMeanResponseTime().toMillis() + "ms");
        System.out.println("95th Percentile: " + result.getRawStats().overall().sampleTime().perc95().toMillis() + "ms");
        
        // More lenient assertions for load test
        assertThat(result.getErrorPercentage())
            .as("Error rate should be acceptable under load")
            .isLessThan(5.0); // Allow up to 5% errors under load
            
        assertThat(result.getTotalSamples())
            .as("All load test samples should be attempted")
            .isEqualTo(50); // 10 threads * 5 iterations
            
        assertThat(result.getMeanResponseTime())
            .as("Response time should be reasonable under load")
            .isLessThan(Duration.ofSeconds(30));
            
        System.out.println("✅ High Load Test PASSED!");
    }
}