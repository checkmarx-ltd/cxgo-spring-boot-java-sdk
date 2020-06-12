package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.cx.CxAuthResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class CxAuthServiceTest {
    private CxAuthService cxAuthService;

    @Mock
    private CxProperties cxProperties;

    @Mock
    private RestTemplate restTemplate;

    private static final String idToken = "id123";

    @Before
    public void init() {
        cxAuthService = new CxAuthService(cxProperties, restTemplate);
        CxAuthResponse token = new CxAuthResponse("123",idToken, 123l, "refresh");
        String clientSecret = "clientSecret123";
        String url = "http://locahost";

        when(restTemplate.postForObject(anyString(),any(),any())).thenReturn(token);
        when(cxProperties.getClientSecret()).thenReturn(clientSecret);
        when(cxProperties.getUrl()).thenReturn(url);
    }

    @Test
    public void test_createAuthHeaders_withParams_shouldBeSuccess(){
        HttpHeaders httpHeaders = cxAuthService.createAuthHeaders();

        assertNotNull(httpHeaders );
        assertEquals(3, httpHeaders.size());
        assertNotNull(httpHeaders.get(httpHeaders.AUTHORIZATION));
        assertEquals(idToken, httpHeaders.get(httpHeaders.AUTHORIZATION).get(0).split(" ")[1].trim());
    }
}
