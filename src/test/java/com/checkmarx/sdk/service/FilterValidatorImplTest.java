package com.checkmarx.sdk.service;

import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.filtering.FilterInput;
import com.checkmarx.sdk.dto.filtering.ScriptedFilter;
import com.checkmarx.sdk.exception.CheckmarxRuntimeException;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class FilterValidatorImplTest {
    private static final String STATUS_RECURRENT = "RECURRENT";
    private static final String STATUS_NEW = "NEW";
    private static final String STATE_URGENT_NAME = "URGENT";
    private static final String STATE_VERIFY_NAME = "TO_VERIFY";
    private static final String SEVERITY_HIGH = "HIGH";
    private static final String SEVERITY_MEDIUM = "MEDIUM";
    private static final String SEVERITY_LOW = "LOW";
    private static final String NAME1 = "CROSS_SITE_HISTORY_MANIPULATION";
    private static final String NAME2 = "CLIENT_POTENTIAL_XSS";
    public static final String PERFORMANCE_TEST_SCRIPT = "finding.severity == 'HIGH' || finding.severity == 'MEDIUM'";
    public static final Duration MAX_ALLOWED_DURATION = Duration.ofSeconds(10);

    @Test
    public void passesFilter_scriptTypicalExample() {
        String scriptText = "finding.severity == 'HIGH' || (finding.severity == 'MEDIUM' && finding.state == 'URGENT')";

        Script script = parse(scriptText);
        verifyScriptResult(script, SEVERITY_HIGH, STATUS_RECURRENT, STATE_URGENT_NAME, NAME1, true);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_RECURRENT, STATE_URGENT_NAME, NAME1, true);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_NEW, STATE_VERIFY_NAME, NAME1, false);
        verifyScriptResult(script, SEVERITY_LOW, STATUS_NEW, STATE_URGENT_NAME, NAME1, false);
    }

    @Test
    public void passesFilter_allPropertiesInScript() {
        String scriptText = "finding.severity == 'MEDIUM' && finding.state == 'TO_VERIFY' && finding.status == 'NEW'" +
                "&& finding.category == 'CROSS_SITE_HISTORY_MANIPULATION'";

        Script script = parse(scriptText);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_NEW, STATE_VERIFY_NAME, NAME1, true);
        verifyScriptResult(script, SEVERITY_HIGH, STATUS_NEW, STATE_VERIFY_NAME, NAME1, false);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_RECURRENT, STATE_VERIFY_NAME, NAME1, false);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_NEW, STATE_URGENT_NAME, NAME1, false);
        verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_NEW, STATE_VERIFY_NAME, NAME2, false);
    }

    @Test
    public void passesFilter_scriptRuntimeError() {
        String unknownObject = "cry.of.surprise == 'present'";
        String unknownProperty = "finding.mystery == 'unsolvable'";

        validateExpectedError(unknownObject);
        validateExpectedError(unknownProperty);
    }

    /**
     * Parsing normally occurs only once during automation flow.
     * However, it takes much longer than script evaluation.
     */
    @Test
    public void passesFilter_parsingPerformance() {
        long start = System.currentTimeMillis();
        parse(PERFORMANCE_TEST_SCRIPT);
        long end = System.currentTimeMillis();

        Duration parseDuration = Duration.ofMillis(end - start);
        log.info("Parsing took {}.", parseDuration);

        assertTrue(MAX_ALLOWED_DURATION.compareTo(parseDuration) >= 0,
                String.format("Script parsing took too long (more than %s).", MAX_ALLOWED_DURATION));
    }

    /**
     * Make sure that filter script evaluation doesn't take too long.
     * Important because multiple findings may be provided.
     */
    @Test
    public void passesFilter_evaluationPerformance() {
        final int EVALUATION_COUNT = 10000;
        Script script = parse(PERFORMANCE_TEST_SCRIPT);
        long start = System.currentTimeMillis();

        for (int i = 0; i < EVALUATION_COUNT; i++) {
            verifyScriptResult(script, SEVERITY_MEDIUM, STATUS_NEW, STATE_VERIFY_NAME, NAME1, true);
            verifyScriptResult(script, SEVERITY_LOW, STATUS_RECURRENT, STATE_URGENT_NAME, NAME1, false);
            verifyScriptResult(script, SEVERITY_HIGH, STATUS_NEW, STATE_VERIFY_NAME, NAME1, true);
            verifyScriptResult(script, SEVERITY_HIGH, STATUS_RECURRENT, STATE_URGENT_NAME, NAME1, true);
        }
        long end = System.currentTimeMillis();

        Duration actualDuration = Duration.ofMillis(end - start);
        log.info("Evaluation took {}.", actualDuration);

        assertTrue(MAX_ALLOWED_DURATION.compareTo(actualDuration) >= 0,
                String.format("Filter evaluation took too long (more than %s).", MAX_ALLOWED_DURATION));    }

    @Test
    public void passesFilter_allSimpleFilters() {
        Filter severity = Filter.builder().type(Filter.Type.SEVERITY).value(SEVERITY_HIGH).build();
        Filter type = Filter.builder().type(Filter.Type.TYPE).value(NAME1).build();
        Filter status = Filter.builder().type(Filter.Type.STATUS).value(STATUS_NEW).build();
        Filter state = Filter.builder().type(Filter.Type.STATE).value(STATE_URGENT_NAME).build();
        List<Filter> filters = Arrays.asList(severity, type, status, state);

        verifySimpleFilterResult(filters, SEVERITY_HIGH, STATUS_NEW, STATE_URGENT_NAME, NAME1, true);
        verifySimpleFilterResult(filters, SEVERITY_MEDIUM, STATUS_NEW, STATE_URGENT_NAME, NAME1, false);
        verifySimpleFilterResult(filters, SEVERITY_HIGH, STATUS_RECURRENT, STATE_URGENT_NAME, NAME1, false);
        verifySimpleFilterResult(filters, SEVERITY_HIGH, STATUS_NEW, STATE_VERIFY_NAME, NAME1, false);
        verifySimpleFilterResult(filters, SEVERITY_HIGH, STATUS_NEW, STATE_URGENT_NAME, NAME2, false);
    }

    private void validateExpectedError(String scriptWithRuntimeError) {
        Script script = parse(scriptWithRuntimeError);

        FilterInput finding = createFilterInput(SEVERITY_LOW, NAME1, STATUS_NEW, STATE_URGENT_NAME); 
        
        FilterConfiguration filterConfiguration = createFilterConfiguration(script);
        FilterValidatorImpl validator = new FilterValidatorImpl();

        try {
            validator.passesFilter(finding, filterConfiguration);
        } catch (Exception e) {
            assertTrue(e instanceof CheckmarxRuntimeException, String.format("Expected %s to be thrown.", CheckmarxRuntimeException.class));
            assertTrue(e.getCause() instanceof GroovyRuntimeException, String.format("Expected exception cause to be %s", GroovyRuntimeException.class));
        }
    }

    private static FilterInput createFilterInput(String severity, String name, String status, String stateName) {
        return FilterInput.builder()
                .id("9389081")
                .severity(severity)
                .category(name)
                .status(status)
                .state(stateName)
                .build();
    }

    private static Script parse(String scriptText) {
        GroovyShell groovyShell = new GroovyShell();
        return groovyShell.parse(scriptText);
    }

    private static void verifyScriptResult(Script script,
                                           String severity,
                                           String status,
                                           String state,
                                           String name,
                                           boolean expectedResult) {
        FilterInput finding = createFilterInput(severity, name, status, state);
        FilterConfiguration filterConfiguration = createFilterConfiguration(script);

        FilterValidatorImpl validator = new FilterValidatorImpl();
        boolean actualResult = validator.passesFilter(finding, filterConfiguration);
        assertEquals(expectedResult, actualResult, "Unexpected script filtering result.");
    }

    private static void verifySimpleFilterResult(List<Filter> filters,
                                                 String severity,
                                                 String status,
                                                 String state,
                                                 String name,
                                                 boolean expectedResult) {
        FilterInput finding = createFilterInput(severity, name, status, state);
        FilterValidatorImpl filterValidator = new FilterValidatorImpl();
        FilterConfiguration filterConfiguration = FilterConfiguration.builder().simpleFilters(filters).build();
        boolean passes = filterValidator.passesFilter(finding, filterConfiguration);
        assertEquals(expectedResult, passes, "Unexpected simple filtering result.");
    }

    private static FilterConfiguration createFilterConfiguration(Script script) {
        ScriptedFilter filter = ScriptedFilter.builder()
                .script(script)
                .build();

        return FilterConfiguration.builder()
                .scriptedFilter(filter)
                .build();
    }
}