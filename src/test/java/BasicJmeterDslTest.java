import static us.abstracta.jmeter.javadsl.JmeterDsl.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import us.abstracta.jmeter.javadsl.core.DslTestPlan;
// import us.abstracta.jmeter.javadsl.core.DslTestPlan.TestPlanChild;
// import us.abstracta.jmeter.javadsl.core.listeners.DslViewResultsTreeListener;
import org.junit.jupiter.api.Test;

public class BasicJmeterDslTest {

    @Test
    public void simpleHttpGetTest() throws Exception {
    // Create a new results folder with current date and timestamp
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String resultsDir = "target/results_" + timestamp;
    Files.createDirectories(Path.of(resultsDir));

    // Define a simple JMeter test plan with JMeter DSL
    DslTestPlan testPlan = testPlan(
        threadGroup(2, 5,
            httpSampler("https://httpbin.org/get")
        ),
        //jtlWriter(resultsDir + "/jtl-results.jtl").withAllFields()
        jtlWriter(resultsDir + "/jtl-results.jtl").withAllFields(true)
    );

    // Run the test plan and get results
    var results = testPlan.run();

    // Assert that the test results are successful
    assertThat(results.overall().sampleTime().mean()).isGreaterThan(Duration.ZERO);
    assertThat(results.overall().errorsCount()).isEqualTo(0);
    }
}