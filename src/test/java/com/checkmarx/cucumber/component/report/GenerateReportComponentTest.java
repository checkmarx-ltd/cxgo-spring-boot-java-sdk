package com.checkmarx.cucumber.component.report;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = { "pretty", "summary", "html:build/cucumber/component/scan", "json:build/cucumber/component/scan/cucumber.json" },
        features = "classpath:cucumber/features/componentTests/generate-report.feature",
        glue = { "com.checkmarx.cucumber.common.steps", "com.checkmarx.cucumber.component.report" },
        tags = " @Component and not @Skip")
public class GenerateReportComponentTest {
}
