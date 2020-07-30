package com.checkmarx.sdk.test;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.cx.CxScanParams;
import com.checkmarx.sdk.service.CxAuthClient;
import com.checkmarx.sdk.service.CxClient;
import com.checkmarx.sdk.service.CxRepoFileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.mock;

@Configuration
public class CxConfigMock {
    @Primary
    @Bean
    public CxRepoFileService getCxRepoFileService(){return mock(CxRepoFileService.class);}

    @Primary
    @Bean
    public RestTemplate getRestTemplate() {
        return mock(RestTemplate.class);
    }

    @Primary
    @Bean
    public CxAuthClient getAuthClient(){ return  mock(CxAuthClient.class); }

    @Primary
    @Bean
    public CxScanParams getCxScanParams(){ return  mock(CxScanParams.class); }

    @Primary
    @Bean
    public CxProperties getCxProperties(){ return  mock(CxProperties.class); }
}
