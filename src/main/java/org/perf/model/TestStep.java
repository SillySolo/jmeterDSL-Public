package org.perf.model;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

public class TestStep {
    private final String name;
    private final String method;
    private final String endpoint;
    private final String body;
    private final String contentType;
    private final Map<String, String> headers;
    private final Duration thinkTime;
    
    private TestStep(Builder builder) {
        this.name = builder.name;
        this.method = builder.method;
        this.endpoint = builder.endpoint;
        this.body = builder.body;
        this.contentType = builder.contentType;
        this.headers = new HashMap<>(builder.headers);
        this.thinkTime = builder.thinkTime;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getName() { return name; }
    public String getMethod() { return method; }
    public String getEndpoint() { return endpoint; }
    public String getBody() { return body; }
    public String getContentType() { return contentType; }
    public Map<String, String> getHeaders() { return headers; }
    public Duration getThinkTime() { return thinkTime; }
    
    public static class Builder {
        private String name;
        private String method;
        private String endpoint;
        private String body;
        private String contentType;
        private Map<String, String> headers = new HashMap<>();
        private Duration thinkTime = Duration.ZERO;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder get(String endpoint) {
            this.method = "GET";
            this.endpoint = endpoint;
            return this;
        }
        
        public Builder post(String endpoint, String body, String contentType) {
            this.method = "POST";
            this.endpoint = endpoint;
            this.body = body;
            this.contentType = contentType;
            return this;
        }
        
        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Builder thinkTime(Duration duration) {
            this.thinkTime = duration;
            return this;
        }
        
        public TestStep build() {
            if (name == null) {
                name = method + " " + endpoint;
            }
            return new TestStep(this);
        }
    }
}