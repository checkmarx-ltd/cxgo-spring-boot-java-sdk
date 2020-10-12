package com.checkmarx.sdk.service;

import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.filtering.FilterInput;
import com.checkmarx.sdk.dto.filtering.ScriptedFilter;
import com.checkmarx.sdk.exception.CheckmarxRuntimeException;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FilterValidatorImpl implements FilterValidator {
    /**
     * An object variable with this name will be passed to the filtering script.
     */
    private static final String INPUT_VARIABLE_NAME = "finding";

    @Override
    public boolean passesFilter(@NotNull FilterInput finding, FilterConfiguration filterConfiguration) {
        boolean result;

        boolean hasSimpleFilters = hasSimpleFilters(filterConfiguration);
        boolean hasScriptedFilter = hasScriptedFilter(filterConfiguration);

        if (hasScriptedFilter && hasSimpleFilters) {
            throw new CheckmarxRuntimeException("Simple filters and scripted filter cannot be used together. " +
                    "Please either specify one of them or don't use filters.");
        } else if (!hasSimpleFilters && !hasScriptedFilter) {
            // No filters => everything passes.
            result = true;
        } else if (hasScriptedFilter) {
            result = passesScriptedFilter(finding, filterConfiguration);
        } else {
            result = passesSimpleFilter(finding, filterConfiguration);
        }
        log.debug("Finding {} {} the filter.", finding.getId(), result ? "passes" : "does not pass");

        return result;
    }

    private static boolean passesScriptedFilter(FilterInput finding, FilterConfiguration filterConfiguration) {
        ScriptedFilter filter = filterConfiguration.getScriptedFilter();
        return evaluateFilterScript(filter.getScript(), finding);
    }

    private static boolean passesSimpleFilter(FilterInput finding, FilterConfiguration filterConfiguration) {
        List<Filter> filters = filterConfiguration.getSimpleFilters();
        return CollectionUtils.isEmpty(filters) || findingPassesFilter(finding, filters);
    }

    private static boolean hasScriptedFilter(FilterConfiguration filterConfiguration) {
        return filterConfiguration != null &&
                filterConfiguration.getScriptedFilter() != null &&
                filterConfiguration.getScriptedFilter().getScript() != null;
    }

    private static boolean hasSimpleFilters(FilterConfiguration filterConfiguration) {
        return filterConfiguration != null &&
                CollectionUtils.isNotEmpty(filterConfiguration.getSimpleFilters());
    }

    private static boolean findingPassesFilter(FilterInput finding, List<Filter> filters) {
        List<String> statuses = new ArrayList<>();
        List<String> states = new ArrayList<>();
        List<String> severity = new ArrayList<>();
        List<String> cwe = new ArrayList<>();
        List<String> category = new ArrayList<>();

        for (Filter filter : filters) {
            List<String> targetList = null;
            Filter.Type type = filter.getType();
            String value = filter.getValue();
            if (type.equals(Filter.Type.STATUS)) {
                targetList = statuses;
            } else if (type.equals(Filter.Type.STATE)) {
                targetList = states;
            } else if (type.equals(Filter.Type.SEVERITY)) {
                targetList = severity;
            } else if (type.equals(Filter.Type.TYPE)) {
                targetList = category;
            } else if (type.equals(Filter.Type.CWE)) {
                targetList = cwe;
            }

            if (targetList != null) {
                targetList.add(value.toUpperCase(Locale.ROOT));
            }
        }

        return fieldMatches(finding.getStatus(), statuses) &&
                fieldMatches(finding.getState(), states) &&
                fieldMatches(finding.getSeverity(), severity) &&
                fieldMatches(finding.getCweId(), cwe) &&
                fieldMatches(finding.getCategory(), category);
    }

    private static boolean evaluateFilterScript(Script script, FilterInput finding) {
        Binding binding = new Binding();
        binding.setVariable(INPUT_VARIABLE_NAME, finding);
        script.setBinding(binding);
        Object rawResult = null;
        try {
            rawResult = script.run();
        } catch (GroovyRuntimeException e) {
            rethrowWithDetailedMessage(e);
        } catch (Exception e) {
            throw new CheckmarxRuntimeException("An unexpected error has occurred while executing the filter script.", e);
        }

        if (rawResult instanceof Boolean) {
            return (boolean) rawResult;
        } else {
            throw new CheckmarxRuntimeException("Filtering script must return a boolean value.");
        }
    }

    private static void rethrowWithDetailedMessage(GroovyRuntimeException cause) {
        List<String> existingFields = Arrays.stream(FilterInput.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());

        String message = String.format("A runtime error has occurred while executing the filter script. " +
                        "Please use %s.<property> in your expressions, where <property> is one of %s.",
                INPUT_VARIABLE_NAME,
                existingFields);

        throw new CheckmarxRuntimeException(message, cause);
    }

    private static boolean fieldMatches(String fieldValue, List<String> allowedValues) {
        return allowedValues.isEmpty() ||
                allowedValues.contains(fieldValue.toUpperCase(Locale.ROOT));
    }
}