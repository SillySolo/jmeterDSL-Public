package org.perf.model;

import org.perf.core.TestConfiguration;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import java.time.Duration;

public class ExecutionResult {
    private final TestPlanStats stats;
    private final TestConfiguration config;
    
    public ExecutionResult(TestPlanStats stats, TestConfiguration config) {
        this.stats = stats;
        this.config = config;
    }
    
    public long getErrorCount() {
        return stats.overall().errorsCount();
    }
    
    public long getTotalSamples() {
        return stats.overall().samplesCount();
    }
    
    public Duration getMeanResponseTime() {
        return stats.overall().sampleTime().mean();
    }
    
    public Duration getMedianResponseTime() {
        return stats.overall().sampleTime().median();
    }
    
    public Duration getMaxResponseTime() {
        return stats.overall().sampleTime().max();
    }
    
    public Duration getMinResponseTime() {
        return stats.overall().sampleTime().min();
    }
    
    public double getErrorPercentage() {
        if (getTotalSamples() == 0) return 0.0;
        return (double) getErrorCount() / getTotalSamples() * 100.0;
    }
    
    public TestPlanStats getRawStats() {
        return stats;
    }
    
    public TestConfiguration getConfig() {
        return config;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ExecutionResult{samples=%d, errors=%d, meanTime=%dms, errorRate=%.2f%%}",
            getTotalSamples(),
            getErrorCount(),
            getMeanResponseTime().toMillis(),
            getErrorPercentage()
        );
    }
}