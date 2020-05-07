package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "projectId",
        "scanId",
        "zipFileName"
})
public class OdFileUpload {
    @JsonProperty("projectId")
    public String projectId;

    @JsonProperty("scanId")
    public String scanId;

    @JsonProperty("zipFileName")
    public String zipFileName;

    @java.beans.ConstructorProperties({"projectId"})
    OdFileUpload(String projectId, String scanId, String zipFileName) {
        this.projectId = projectId;
        this.scanId = scanId;
        this.zipFileName = zipFileName;
    }

    public static com.checkmarx.sdk.dto.od.OdFileUpload.OdFileUploadBuilder builder() {
        return new com.checkmarx.sdk.dto.od.OdFileUpload.OdFileUploadBuilder();
    }

    public String getProjectId() {
        return this.projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public static class OdFileUploadBuilder {
        private String projectId;
        private String scanId;
        private String zipFileName;

        OdFileUploadBuilder() {
        }

        public OdFileUploadBuilder projectId(String projectId, String scanId, String zipFileName) {
            this.projectId = projectId;
            this.scanId = scanId;
            this.zipFileName = zipFileName;
            return this;
        }

        public OdFileUpload build() {
            return new OdFileUpload(projectId, scanId, zipFileName);
        }

        public String toString() {
            return "CxScan.CxScanBuilder(projectId=" + this.projectId  + ")";
        }
    }
}
