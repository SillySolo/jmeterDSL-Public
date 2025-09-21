package org.perf.builder;

import org.perf.model.TestStep;
import java.time.Duration;

public class RequestBuilder {
    private final PerformanceTestBuilder parent;
    private TestStep.Builder stepBuilder;
    
    public RequestBuilder(PerformanceTestBuilder parent) {
        this.parent = parent;
        this.stepBuilder = TestStep.builder();
    }
    
    public RequestBuilder name(String name) {
        stepBuilder.name(name);
        return this;
    }
    
    public RequestBuilder get(String endpoint) {
        stepBuilder.get(endpoint);
        return this;
    }
    
    public RequestBuilder post(String endpoint, String body) {
        stepBuilder.post(endpoint, body, "application/json");
        return this;
    }
    
    public RequestBuilder post(String endpoint, String body, String contentType) {
        stepBuilder.post(endpoint, body, contentType);
        return this;
    }
    
    public RequestBuilder header(String key, String value) {
        stepBuilder.header(key, value);
        return this;
    }
    
    public RequestBuilder thinkTime(Duration duration) {
        stepBuilder.thinkTime(duration);
        return this;
    }
    
    public PerformanceTestBuilder and() {
        parent.addTestStep(stepBuilder.build());
        return parent;
    }
}