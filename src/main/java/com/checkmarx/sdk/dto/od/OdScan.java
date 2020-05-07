package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "projectId"
})
public class OdScan {
    @JsonProperty("projectId")
    public Integer projectId;

    @java.beans.ConstructorProperties({"projectId"})
    OdScan(Integer projectId) {
        this.projectId = projectId;
    }

    public static com.checkmarx.sdk.dto.od.OdScan.OdScanBuilder builder() {
        return new com.checkmarx.sdk.dto.od.OdScan.OdScanBuilder();
    }

    public Integer getProjectId() {
        return this.projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String toString() {
        return "CxScan(projectId=" + this.getProjectId() + ")";
    }

    public static class OdScanBuilder {
        private Integer projectId;

        OdScanBuilder() {
        }

        public com.checkmarx.sdk.dto.od.OdScan.OdScanBuilder projectId(Integer projectId) {
            this.projectId = projectId;
            return this;
        }

        public com.checkmarx.sdk.dto.od.OdScan build() {
            return new com.checkmarx.sdk.dto.od.OdScan(projectId);
        }

        public String toString() {
            return "CxScan.CxScanBuilder(projectId=" + this.projectId  + ")";
        }
    }
}
