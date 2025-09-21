package org.perf.builder;

import org.perf.core.TestConfiguration;
import org.perf.core.TestExecutor;
import org.perf.model.TestStep;
import org.perf.model.ExecutionResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PerformanceTestBuilder {
    private TestConfiguration config;
    private ThreadGroupBuilder threadGroupBuilder;
    private List<TestStep> testSteps = new ArrayList<>();
    
    private PerformanceTestBuilder(TestConfiguration config) {
        this.config = config;
        this.threadGroupBuilder = new ThreadGroupBuilder();
    }
    
    public static PerformanceTestBuilder create(TestConfiguration config) {
        return new PerformanceTestBuilder(config);
    }
    
    public PerformanceTestBuilder withThreads(int users) {
        this.threadGroupBuilder.users(users);
        return this;
    }
    
    public PerformanceTestBuilder withIterations(int iterations) {
        this.threadGroupBuilder.iterations(iterations);
        return this;
    }
    
    public PerformanceTestBuilder withRampUp(Duration rampUp) {
        this.threadGroupBuilder.rampUp(rampUp);
        return this;
    }
    
    public RequestBuilder addRequest() {
        return new RequestBuilder(this);
    }
    
    // Package-private method for RequestBuilder to add steps
    void addTestStep(TestStep step) {
        this.testSteps.add(step);
    }
    
    // FIXED: Changed method signature to match TestExecutor expectations
    public ExecutionResult execute() throws Exception {
        TestExecutor executor = new TestExecutor(config);
        ThreadGroupBuilder.ThreadGroupConfig threadConfig = threadGroupBuilder.build();
        return executor.execute(threadConfig, testSteps);
    }
    
    // Getters
    public TestConfiguration getConfig() { return config; }
    public List<TestStep> getTestSteps() { return testSteps; }
}