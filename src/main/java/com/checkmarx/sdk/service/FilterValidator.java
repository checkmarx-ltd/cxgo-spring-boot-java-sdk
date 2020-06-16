package com.checkmarx.sdk.service;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.od.OdScanQueryCategory;
import com.checkmarx.sdk.dto.od.OdScanResultItem;

import javax.validation.constraints.NotNull;

/**
 * Checks if SAST results pass provided filters.
 */
public interface FilterValidator {
    /**
     * Check if a finding and its group meet the filter criteria
     *
     * @param findingGroup        the parent of this finding. Container for findings with the same vulnerability type.
     * @param finding             a finding to check against the filter
     * @param filterConfiguration filters to check against
     * @return a value indicating whether the finding meets the filter criteria
     */
    boolean passesFilter(@NotNull OdScanQueryCategory findingGroup,
                         @NotNull OdScanResultItem finding,
                         FilterConfiguration filterConfiguration);
}
