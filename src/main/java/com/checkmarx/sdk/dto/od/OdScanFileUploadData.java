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



    /* Sample Data
    "data": {
        "url": "https://s3.amazonaws.com/cx-customers-ppe",
                "fields": {
                    "key": "cx-customer-71/cx-project-10008/SAST/cx-scan-10060/sources.zip",
                    "bucket": "cx-customers-ppe",
                    "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
                    "X-Amz-Credential": "ASIARN7ZVCHOUYK4PRF3/20200507/us-east-1/s3/aws4_request",
                    "X-Amz-Date": "20200507T012604Z",
                    "X-Amz-Security-Token": "IQoJb3JpZ2luX2VjEHoaCXVzLWVhc3QtMSJHMEUCIQCX5Xrxz/v2KXlaOs43PSp1oR6S0Y7+53YrVLveUgu3ngIgD+uPde4hPP50XMji2uEhbdahpMVdV9Prrx6rP6XW5XUq2QEIs///////////ARABGgwwOTg3NzA5NDg1NzMiDEagltFXFONHkyCiZSqtAWacAasDi0TFDJfQ49CTU0qKqpj0uHTCryqU9x+xAavn20U1zhaSz1Aw2KNMgYnuTF0UFkHGAu+h4HR2apO9JFAejdgSBeYWVK8sg+DtkjmKT7Tv0Bfi1kzdUslMMa64fK/DhE/G87Zg7N4aPrIy9ZlYTLvy2B2Ae8qqVEL1vi9Xc9+7pfUctPJTCs6/Ok54kEGzTya6KPyMOwKDoyZNQR5pYipOLvNCrIffnxfSMKrHzfUFOuABprFM6uBXbGMOdkpF+M+UkUjJtDnz6ktc8tvePgjo9j1dQBdu+u+YJ80Q4az72TAhUzDLFeoCKnnN7Tkk+7yKCsMBkuImfKDLRIvlaLR+HNSxpFk10vKer5jHX48uA0NlrV0hKXjAHil7FKVpPBUNM2ruVqvskbMU3knEUJ4Uoxt/WtxTKhSGyZ2Vy7r6UX9e2NzYR1hOY0vBkvKWPBREbyjydyDFyDe+qcChqdbplhGeo6KJd/6wXqWrWB/hIQ9NE3Nd0Y2QxyLyIMfEplPOdkUQCp1kBxFdHHFdvDUXd6A=",
                    "Policy": "eyJleHBpcmF0aW9uIjoiMjAyMC0wNS0wN1QwMjoyNjowNFoiLCJjb25kaXRpb25zIjpbWyJjb250ZW50LWxlbmd0aC1yYW5nZSIsMSwzMTQ1NzI4MDBdLHsia2V5IjoiY3gtY3VzdG9tZXItNzEvY3gtcHJvamVjdC0xMDAwOC9TQVNUL2N4LXNjYW4tMTAwNjAvc291cmNlcy56aXAifSx7ImJ1Y2tldCI6ImN4LWN1c3RvbWVycy1wcGUifSx7IlgtQW16LUFsZ29yaXRobSI6IkFXUzQtSE1BQy1TSEEyNTYifSx7IlgtQW16LUNyZWRlbnRpYWwiOiJBU0lBUk43WlZDSE9VWUs0UFJGMy8yMDIwMDUwNy91cy1lYXN0LTEvczMvYXdzNF9yZXF1ZXN0In0seyJYLUFtei1EYXRlIjoiMjAyMDA1MDdUMDEyNjA0WiJ9LHsiWC1BbXotU2VjdXJpdHktVG9rZW4iOiJJUW9KYjNKcFoybHVYMlZqRUhvYUNYVnpMV1ZoYzNRdE1TSkhNRVVDSVFDWDVYcnh6L3YyS1hsYU9zNDNQU3Axb1I2UzBZNys1M1lyVkx2ZVVndTNuZ0lnRCt1UGRlNGhQUDUwWE1qaTJ1RWhiZGFocE1WZFY5UHJyeDZyUDZYVzVYVXEyUUVJcy8vLy8vLy8vLy8vQVJBQkdnd3dPVGczTnpBNU5EZzFOek1pREVhZ2x0RlhGT05Ia3lDaVpTcXRBV2FjQWFzRGkwVEZESmZRNDlDVFUwcUtxcGowdUhUQ3J5cVU5eCt4QWF2bjIwVTF6aGFTejFBdzJLTk1nWW51VEYwVUZrSEdBdStoNEhSMmFwTzlKRkFlamRnU0JlWVdWSzhzZytEdGtqbUtUN1R2MEJmaTFremRVc2xNTWE2NGZLL0RoRS9HODdaZzdONGFQckl5OVpsWVRMdnkyQjJBZThxcVZFTDF2aTlYYzkrN3BmVWN0UEpUQ3M2L09rNTRrRUd6VHlhNktQeU1Pd0tEb3laTlFSNXBZaXBPTHZOQ3JJZmZueGZTTUtySHpmVUZPdUFCcHJGTTZ1QlhiR01PZGtwRitNK1VrVWpKdERuejZrdGM4dHZlUGdqbzlqMWRRQmR1K3UrWUo4MFE0YXo3MlRBaFV6RExGZW9DS25uTjdUa2srN3lLQ3NNQmt1SW1mS0RMUkl2bGFMUitITlN4cEZrMTB2S2VyNWpIWDQ4dUEwTmxyVjBoS1hqQUhpbDdGS1ZwUEJVTk0ycnVWcXZza2JNVTNrbkVVSjRVb3h0L1d0eFRLaFNHeVoyVnk3cjZVWDllMk56WVIxaE9ZMHZCa3ZLV1BCUkVieWp5ZHlERnlEZStxY0NocWRicGxoR2VvNktKZC82d1hxV3JXQi9oSVE5TkUzTmQwWTJReHlMeUlNZkVwbFBPZGtVUUNwMWtCeEZkSEhGZHZEVVhkNkE9In1dfQ==",
                    "X-Amz-Signature": "d4f634b66ce61f7b968aa230e8e9a928c3db891676a8ee4a00a1d9a741e8a54e"
        }
    */


}

