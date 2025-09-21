// ============================================================================
// COMPLETE FIXED TestExecutor.java
// ============================================================================

package org.perf.core;

import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

import org.perf.model.ExecutionResult;
import org.perf.model.TestStep;
import org.perf.builder.ThreadGroupBuilder;

import us.abstracta.jmeter.javadsl.core.DslTestPlan;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup;
import us.abstracta.jmeter.javadsl.http.DslHttpSampler;

import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

public class TestExecutor {
    private final TestConfiguration config;
    
    // Synchronization to prevent marshalling conflicts
    private static final ReentrantLock EXECUTION_LOCK = new ReentrantLock();
    private static volatile boolean jmeterInitialized = false;
    
    public TestExecutor(TestConfiguration config) {
        this.config = config;
    }
    
    public ExecutionResult execute(ThreadGroupBuilder.ThreadGroupConfig threadConfig, 
                                  List<TestStep> testSteps) throws Exception {
        
        String threadName = Thread.currentThread().getName();
        
        // Initialize JMeter once
        synchronized (TestExecutor.class) {
            if (!jmeterInitialized) {
                System.out.println("🔧 [" + threadName + "] Initializing JMeter environment...");
                initializeJMeterEnvironment();
                jmeterInitialized = true;
                System.out.println("✅ [" + threadName + "] JMeter environment initialized");
            }
        }
        if (!jmeterInitialized) {
            Thread.sleep(2000);
        }
        
        // Serialize execution to prevent marshalling conflicts
        EXECUTION_LOCK.lock();
        try {
            System.out.println("🔒 [" + threadName + "] Acquired execution lock for: " + config.getTestName());
            
            // Stagger execution
            long delay = Math.abs(threadName.hashCode() % 1000) + 500;
            Thread.sleep(delay);
            
            return executeTestPlan(threadConfig, testSteps, threadName);
            
        } finally {
            System.out.println("🔓 [" + threadName + "] Released execution lock for: " + config.getTestName());
            EXECUTION_LOCK.unlock();
            Thread.sleep(300);
        }
    }
    
    private ExecutionResult executeTestPlan(ThreadGroupBuilder.ThreadGroupConfig threadConfig, 
                                          List<TestStep> testSteps, String threadName) throws Exception {
        
        // Convert test steps to JMeter DSL samplers
        List<BaseThreadGroup.ThreadGroupChild> threadGroupChildren = new ArrayList<>();
        
        for (TestStep step : testSteps) {
            DslHttpSampler sampler = createSampler(step);
            threadGroupChildren.add(sampler);
            
            if (!step.getThinkTime().isZero()) {
                threadGroupChildren.add(constantTimer(step.getThinkTime()));
            }
        }
        
        // Create thread group with unique name
        String uniqueGroupName = config.getTestName() + "-" + threadName + "-" + System.currentTimeMillis();
        var threadGroup = threadGroup(
            uniqueGroupName,
            threadConfig.getUsers(),
            threadConfig.getIterations(),
            threadGroupChildren.toArray(BaseThreadGroup.ThreadGroupChild[]::new)
        );
        
        // Build test plan components
        List<DslTestPlan.TestPlanChild> testPlanChildren = new ArrayList<>();
        testPlanChildren.add(threadGroup);
        
        // Add JTL writer
        Path jtlFile = config.getResultsDirectory().resolve("results.jtl");
        testPlanChildren.add(
            jtlWriter(jtlFile.toString())
                .withAllFields(true)
                .saveAsXml(false)
        );
        
        DslTestPlan testPlan = testPlan(
            testPlanChildren.toArray(DslTestPlan.TestPlanChild[]::new)
        );
        
        System.out.println("🏃 [" + threadName + "] Executing test plan: " + config.getTestName());
        
        TestPlanStats stats;
        try {
            stats = testPlan.run();
        } catch (Exception e) {
            System.err.println("❌ [" + threadName + "] Test execution failed: " + e.getMessage());
            throw new RuntimeException("Test execution failed for " + config.getTestName(), e);
        }
        
        System.out.println("✅ [" + threadName + "] Test execution completed: " + config.getTestName());
        
        return new ExecutionResult(stats, config);
    }
    
    private void initializeJMeterEnvironment() {
        try {
            // Set JMeter properties for better parallel execution
            System.setProperty("jmeter.save.saveservice.thread_counts", "true");
            System.setProperty("jmeter.save.saveservice.assertion_results_failure_message", "false");
            System.setProperty("jmeter.save.saveservice.successful", "true");
            
            Thread.sleep(1000);
            System.out.println("🔧 JMeter environment initialization completed");
        } catch (Exception e) {
            System.err.println("⚠️ JMeter initialization warning: " + e.getMessage());
        }
    }

    private DslHttpSampler createSampler(TestStep step) {
    String fullUrl = config.getBaseUrl() + step.getEndpoint();
    String threadName = Thread.currentThread().getName();
    
    String uniqueSamplerName = step.getName() + "-" + threadName;
    DslHttpSampler sampler = httpSampler(uniqueSamplerName, fullUrl)
        .connectionTimeout(config.getConnectionTimeout())
        .responseTimeout(config.getResponseTimeout());
    
    step.getHeaders().forEach(sampler::header);
    
    String method = step.getMethod().toUpperCase();
    switch (method) {
        case "POST":
            sampler.method("POST");
            if (step.getBody() != null) {
                sampler.body(step.getBody());
            }
            if (step.getContentType() != null) {
                sampler.header("Content-Type", step.getContentType());
            }
            break;
        case "PUT":
            sampler.method("PUT");
            if (step.getBody() != null) {
                sampler.body(step.getBody());
            }
            if (step.getContentType() != null) {
                sampler.header("Content-Type", step.getContentType());
            }
            break;
        case "PATCH":
            sampler.method("PATCH");
            if (step.getBody() != null) {
                sampler.body(step.getBody());
            }
            if (step.getContentType() != null) {
                sampler.header("Content-Type", step.getContentType());
            }
            break;
        case "DELETE":
            sampler.method("DELETE");
            break;
        case "GET":
        default:
            // GET is default
            break;
    }
    return sampler;
    }
}