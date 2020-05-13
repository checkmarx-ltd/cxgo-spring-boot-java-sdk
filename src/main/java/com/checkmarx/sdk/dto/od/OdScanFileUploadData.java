package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "url",
        "fields"
})
public class OdScanFileUploadData {
    @JsonProperty("url")
    private String url;

    @JsonProperty("fields")
    private OdScanFileUploadFields fields;

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setId(String url) {
        this.url = url;
    }

    @JsonProperty("fields")
    public OdScanFileUploadFields getFields() {
        return fields;
    }

    @JsonProperty("urfieldsl")
    public void setFields(OdScanFileUploadFields fields) {
        this.fields = fields;
    }
}

