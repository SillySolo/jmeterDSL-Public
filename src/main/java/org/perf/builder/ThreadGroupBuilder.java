package org.perf.builder;

import java.time.Duration;

public class ThreadGroupBuilder {
    private int users = 1;
    private int iterations = 1;
    private Duration rampUp = Duration.ofSeconds(1);
    
    public ThreadGroupBuilder users(int users) {
        this.users = users;
        return this;
    }
    
    public ThreadGroupBuilder iterations(int iterations) {
        this.iterations = iterations;
        return this;
    }
    
    public ThreadGroupBuilder rampUp(Duration rampUp) {
        this.rampUp = rampUp;
        return this;
    }
    
    public ThreadGroupConfig build() {
        return new ThreadGroupConfig(users, iterations, rampUp);
    }
    
    public static class ThreadGroupConfig {
        private final int users;
        private final int iterations;
        private final Duration rampUp;
        
        public ThreadGroupConfig(int users, int iterations, Duration rampUp) {
            this.users = users;
            this.iterations = iterations;
            this.rampUp = rampUp;
        }
        
        public int getUsers() { return users; }
        public int getIterations() { return iterations; }
        public Duration getRampUp() { return rampUp; }
    }
}