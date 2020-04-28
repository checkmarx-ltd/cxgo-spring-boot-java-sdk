package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.cx.CxAuthResponse;
import com.checkmarx.sdk.exception.CheckmarxLegacyException;
import com.checkmarx.sdk.exception.InvalidCredentialsException;
import com.checkmarx.sdk.utils.ScanUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxAuthService implements CxAuthClient{
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxAuthService.class);


    @Override
    public String getAuthToken(String username, String password, String clientId, String clientSecret, String scope) throws InvalidCredentialsException {
        return null;
    }

    @Override
    public String getSoapAuthToken(String username, String password) throws InvalidCredentialsException {
        return null;
    }

    @Override
    public String legacyLogin(String username, String password) throws InvalidCredentialsException {
        return null;
    }

    @Override
    public HttpHeaders createAuthHeaders() {
        return null;
    }

    @Override
    public String getCurrentToken() {
        return null;
    }

    @Override
    public String getCurrentSoapToken() {
        return null;
    }

    @Override
    public String getLegacySession() {
        return null;
    }
}
