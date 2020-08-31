package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanResults {

    @JsonProperty("sast")
    private List<SASTScanResult> sast;

    //@JsonProperty("sca")
   //private SASTScanResult sast;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("sast")
    public List<SASTScanResult> getSast() {
        return sast;
    }

    @JsonProperty("sast")
    public void setSast(List<SASTScanResult> sast) {
        this.sast = sast;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

}
