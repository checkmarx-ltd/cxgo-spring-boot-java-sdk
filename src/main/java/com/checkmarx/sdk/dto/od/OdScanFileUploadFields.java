package com.checkmarx.sdk.dto.od;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "key",
        "bucket",
        "X-Amz-Algorithm",
        "X-Amz-Credential",
        "X-Amz-Date",
        "X-Amz-Security-Token",
        "Policy",
        "X-Amz-Signature"
})
public class OdScanFileUploadFields {
    @JsonProperty("key")
    private String key;

    @JsonProperty("bucket")
    private String bucket;

    @JsonProperty("X-Amz-Algorithm")
    private String x_Amz_Algorithm;

    @JsonProperty("X-Amz-Credential")
    private String x_Amz_Credential;

    @JsonProperty("X-Amz-Date")
    private String x_Amz_Date;

    @JsonProperty("X-Amz-Security-Token")
    private String x_Amz_Security_Token;

    @JsonProperty("Policy")
    private String policy;

    @JsonProperty("X-Amz-Signature")
    private String x_Amz_Signature;

    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    @JsonProperty("key")
    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty("bucket")
    public String getBucket() {
        return bucket;
    }

    @JsonProperty("bucket")
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @JsonProperty("X-Amz-Algorithm")
    public String getX_Amz_Algorithm() {
        return x_Amz_Algorithm;
    }

    @JsonProperty("X-Amz-Algorithm")
    public void setX_Amz_Algorithm(String x_Amz_Algorithm) {
        this.x_Amz_Algorithm = x_Amz_Algorithm;
    }

    @JsonProperty("X-Amz-Credential")
    public String getX_Amz_Credential() {
        return x_Amz_Credential;
    }

    @JsonProperty("X-Amz-Credential")
    public void setX_Amz_Credential(String x_Amz_Credential) {
        this.x_Amz_Credential = x_Amz_Credential;
    }

    @JsonProperty("X-Amz-Date")
    public String getX_Amz_Date() {
        return x_Amz_Date;
    }

    @JsonProperty("X-Amz-Date")
    public void setX_Amz_Date(String x_Amz_Date) {
        this.x_Amz_Date = x_Amz_Date;
    }

    @JsonProperty("X-Amz-Security-Token")
    public String getX_Amz_Security_Token() {
        return x_Amz_Security_Token;
    }

    @JsonProperty("X-Amz-Security-Token")
    public void setX_Amz_Security_Token(String x_Amz_Security_Token) {
        this.x_Amz_Security_Token = x_Amz_Security_Token;
    }

    @JsonProperty("Policy")
    public String getPolicy() {
        return policy;
    }

    @JsonProperty("Policy")
    public void setPolicy(String policy) {
        this.policy = policy;
    }

    @JsonProperty("X-Amz-Signature")
    public String getX_Amz_Signature() {
        return x_Amz_Signature;
    }

    @JsonProperty("X-Amz-Signature")
    public void setX_Amz_Signature(String x_Amz_Signature) {
        this.x_Amz_Signature = x_Amz_Signature;
    }

}
