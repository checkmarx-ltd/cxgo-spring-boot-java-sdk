package com.checkmarx.sdk.service;

import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.filtering.FilterInput;

import javax.validation.constraints.NotNull;

/**
 * Checks if a specific scan result item (finding) passes provided filters.
 */
public interface FilterValidator {
    /**
     * @param filterConfiguration filters to check against
     * @return a value indicating whether the finding meets the filter criteria
     */
    boolean passesFilter(@NotNull FilterInput finding,
                         FilterConfiguration filterConfiguration);
}
