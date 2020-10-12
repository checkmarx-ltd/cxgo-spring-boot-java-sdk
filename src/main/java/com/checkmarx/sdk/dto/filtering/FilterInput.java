package com.checkmarx.sdk.dto.filtering;

import com.checkmarx.sdk.dto.od.OdScanResultItem;
import com.checkmarx.sdk.dto.od.SASTScanResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standardized input to {@link com.checkmarx.sdk.service.FilterValidator}, independent of specific scanner type.
 */
@Builder
@Getter
@Setter
@Slf4j
public class FilterInput {
    private static final Map<Integer, SASTScanResult.State> STATE_ID_TO_NAME = Arrays.stream(SASTScanResult.State.values())
            .collect(Collectors.toMap(SASTScanResult.State::getValue, Function.identity()));

    private final String id;

    /**
     * This field is also known as 'title'.
     */
    private final String category;

    private final String cweId;
    private final String severity;
    private final String status;
    private final String state;


    public static FilterInput getInstance(SASTScanResult sastScanResult, OdScanResultItem odScanResultItem) {
        // CxOD currently does not have CWE info
        // TODO: update this when CWE info is available
        return FilterInput.builder()
                .category(odScanResultItem.getTitle().toUpperCase(Locale.ROOT))
                .id(sastScanResult.getId().toString())
                .severity(sastScanResult.getSeverity().getSeverity())
                .state(getStateName(sastScanResult))
                .status(sastScanResult.getStatus().getStatus())
                .build();
    }

    private static String getStateName(SASTScanResult sastScanResult) {
        SASTScanResult.State state = STATE_ID_TO_NAME.get(sastScanResult.getState());
        if (state == null) {
            log.warn("Unknown state ID for a CxGO-SAST result: {}. The state will be ignored during filtering.",
                    sastScanResult.getState());
        }

        return state != null ? state.toString() : null;
    }
}