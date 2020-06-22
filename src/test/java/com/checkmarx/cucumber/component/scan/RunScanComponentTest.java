package com.checkmarx.cucumber.component.scan;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = { "pretty", "summary", "html:build/cucumber/component/scan", "json:build/cucumber/component/scan/cucumber.json" },
        features = "classpath:cucumber/features/componentTests/scans.feature",
        glue = { "com.checkmarx.cucumber.common.steps", "com.checkmarx.cucumber.component.scan" },
        tags = " @Component and not @Skip")
public class RunScanComponentTest {
}
