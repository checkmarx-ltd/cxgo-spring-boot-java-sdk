package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.exception.InvalidCredentialsException;
import com.checkmarx.sdk.dto.cx.CxAuthResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxAuthService implements CxAuthClient {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxAuthService.class);
    private final CxProperties cxProperties;
    private final RestTemplate restTemplate;
    private CxAuthResponse token = null;
    //
    /// REST API end-points
    //
    private static final String GET_SESSION_TOKEN = "/tokens/tokens/auth";

    public CxAuthService(CxProperties cxProperties, @Qualifier("flowRestTemplate") RestTemplate restTemplate) {
        this.cxProperties = cxProperties;
        this.restTemplate = restTemplate;
    }

    private void getAuthToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noCache());
        HttpEntity req = new HttpEntity<>(getJSONTokenReq(), headers);
        this.token = restTemplate.postForObject(
                cxProperties.getUrl().concat(GET_SESSION_TOKEN),
                req,
                CxAuthResponse.class);
    }

    @Override
    public HttpHeaders createAuthHeaders() {
        if (token == null || isTokenExpired()) {
            getAuthToken();
        }
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setCacheControl(CacheControl.noCache());
        httpHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer ".concat(token.getIdToken()));
        return httpHeaders;
    }

    private boolean isTokenExpired() {
        //this.token.getExpiresIn();
        /*
        if (tokenExpires == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(tokenExpires);

         */
        return false;
    }

    @Override
    public String getCurrentToken() {
        return token.getAccessToken();
    }

    /**
     * Create JSON http request body for passing oAuth token to CxOD
     *
     * @return String representation of the token
     */
    private String getJSONTokenReq() {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", cxProperties.getClientSecret());
        } catch (JSONException e) {
            log.error("Error creating JSON Token Request object - JSON object will be empty");
        }
        return requestBody.toString();
    }

    @Override
    public String getAuthToken(String username, String password, String clientId, String clientSecret, String scope) throws InvalidCredentialsException {
        return null;
    }

    // TODO: jeffa, I'm not sure if this is required anymore?
    @Override
    public String getSoapAuthToken(String username, String password) throws InvalidCredentialsException {
        return null;
    }

    // TODO: jeffa, I'm not sure if this is required anymore?
    @Override
    public String legacyLogin(String username, String password) throws InvalidCredentialsException {
        return null;
    }

    // TODO: jeffa, I'm not sure if this is required anymore?
    @Override
    public String getCurrentSoapToken() {
        return null;
    }

    // TODO: jeffa, I'm not sure if this is required anymore?
    @Override
    public String getLegacySession() {
        return null;
    }
}
