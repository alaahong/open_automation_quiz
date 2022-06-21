package cn.ianzhang.automation;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;


@RunWith(Cucumber.class)
@CucumberOptions(features = "src/test/resources",
        plugin = {"pretty", "html:target/classes/static/features/index.html"},
        glue={"SubmitForm"}
)
public class RunCucumberTest {
}
