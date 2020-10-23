package com.checkmarx.sdk.service;

import com.checkmarx.sdk.ShardManager.ShardSessionTracker;
import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.CxUser;
import com.checkmarx.sdk.exception.CheckmarxLegacyException;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import java.util.List;

/**
 * Checkmarx SOAP WebService Client
 */
@Component
public class CxLegacyService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxLegacyService.class);

    public CxLegacyService() {

    }
}
