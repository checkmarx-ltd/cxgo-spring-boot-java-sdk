package com.checkmarx.sdk.config;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.Charset;

@Configuration
public class CxConfig {

    private final CxProperties properties;

    public CxConfig(CxProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "cxRestTemplate")
    public RestTemplate getRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        HttpComponentsClientHttpRequestFactory requestFactory = new
                HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().useSystemProperties().build());
        requestFactory.setConnectTimeout(properties.getHttpConnectionTimeout());
        requestFactory.setReadTimeout(properties.getHttpReadTimeout());
        restTemplate.setRequestFactory(requestFactory);

        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(Charset.forName("UTF-8")));
        return restTemplate;
    }
}