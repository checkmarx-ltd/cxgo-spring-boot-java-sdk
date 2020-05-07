package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "projectId",
        "scanId",
        "s3FilePath",
        "typeIds"
})
public class OdScanTrigger {
    @JsonProperty("projectId")
    public String projectId;

    @JsonProperty("scanId")
    public String scanId;

    @JsonProperty("s3FilePath")
    public String s3FilePath;

    @JsonProperty("typeIds")
    public List<String> typeIds;

    @java.beans.ConstructorProperties({"projectId", "scanId", "s3FilePath", "typeIds"})
    OdScanTrigger(String projectId, String scanId, String s3FilePath, List<String> typeIds) {
        this.projectId = projectId;
        this.scanId = scanId;
        this.s3FilePath = s3FilePath;
        this.typeIds = typeIds;
    }

    public static OdScanTriggerBuilder builder() {
        return new OdScanTriggerBuilder();
    }

    public String getProjectId() {
        return this.projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public static class OdScanTriggerBuilder {
        private String projectId;
        private String scanId;
        private String s3FilePath;
        private List<String> typeIds;

        OdScanTriggerBuilder() {
        }

        public OdScanTriggerBuilder projectId(String projectId, String scanId, String s3FilePath, List<String> typeIds) {
            this.projectId = projectId;
            this.scanId = scanId;
            this.s3FilePath = s3FilePath;
            this.typeIds = typeIds;
            return this;
        }

        public OdScanTrigger build() {
            return new OdScanTrigger(projectId, scanId, s3FilePath, typeIds);
        }
    }
}

