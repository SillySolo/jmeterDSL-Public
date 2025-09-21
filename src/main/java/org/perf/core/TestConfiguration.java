package org.perf.core;

import java.time.Duration;
import java.nio.file.Path;

public class TestConfiguration {
    private final String testName;
    private final String baseUrl;
    private final Path resultsDirectory;
    private final Duration connectionTimeout;
    private final Duration responseTimeout;
    private final boolean generateHtmlReport;
    
    private TestConfiguration(Builder builder) {
        this.testName = builder.testName;
        this.baseUrl = builder.baseUrl;
        this.resultsDirectory = builder.resultsDirectory;
        this.connectionTimeout = builder.connectionTimeout;
        this.responseTimeout = builder.responseTimeout;
        this.generateHtmlReport = builder.generateHtmlReport;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getTestName() { return testName; }
    public String getBaseUrl() { return baseUrl; }
    public Path getResultsDirectory() { return resultsDirectory; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public boolean shouldGenerateHtmlReport() { return generateHtmlReport; }
    
    public static class Builder {
        private String testName = "Performance Test";
        private String baseUrl;
        private Path resultsDirectory;
        private Duration connectionTimeout = Duration.ofSeconds(10);
        private Duration responseTimeout = Duration.ofSeconds(30);
        private boolean generateHtmlReport = false; // FIXED: Default to false for parallel execution
        
        public Builder testName(String testName) {
            this.testName = testName;
            return this;
        }
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder resultsDirectory(Path resultsDirectory) {
            this.resultsDirectory = resultsDirectory;
            return this;
        }
        
        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = timeout;
            return this;
        }
        
        public Builder responseTimeout(Duration timeout) {
            this.responseTimeout = timeout;
            return this;
        }
        
        public Builder generateHtmlReport(boolean generate) {
            this.generateHtmlReport = generate;
            return this;
        }
        
        public TestConfiguration build() {
            return new TestConfiguration(this);
        }
    }
}