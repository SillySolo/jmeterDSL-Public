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

public class BasicJmeterDslTest {
    
    private TestConfiguration config;
    
    @BeforeEach
    void setUp() throws Exception {
        config = TestConfiguration.builder()
            .testName("API Load Test")
            .baseUrl("https://httpbin.org")
            .resultsDirectory(FileUtils.createTimestampedResultsDir())
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(30))
            .generateHtmlReport(true)
            .build();
    }

    @Test
    @DisplayName("Simple GET Request Test")
    public void simpleGetTest() throws Exception {
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(2)
            .withIterations(5)
            .withRampUp(Duration.ofSeconds(1))
            .addRequest()
                .name("Get Users")
                .get("/get")
                .header("Accept", "application/json")
                .thinkTime(Duration.ofMillis(100))
            .and()
            .execute();
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(10);
        assertThat(result.getMeanResponseTime()).isLessThan(Duration.ofSeconds(5));
    }
    
    @Test
    @DisplayName("Multi-Step API Test")
    public void multiStepApiTest() throws Exception {
        ExecutionResult result = PerformanceTestBuilder.create(config)
            .withThreads(3)
            .withIterations(2)
            .addRequest()
                .name("Login")
                .get("/get")
                .header("Authorization", "Bearer test-token")
            .and()
            .addRequest()
                .name("Create User")
                .post("/post", "{\"name\":\"John\",\"age\":30}")
                .header("Content-Type", "application/json")
                .thinkTime(Duration.ofMillis(500))
            .and()
            .addRequest()
                .name("Verify Status")
                .get("/status/200")
            .and()
            .execute();
        
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getTotalSamples()).isEqualTo(18); // 3 threads * 2 iterations * 3 requests
    }
}