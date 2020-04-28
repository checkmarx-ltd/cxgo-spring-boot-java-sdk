package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.CxUser;
import com.checkmarx.sdk.exception.CheckmarxException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CxUserService implements CxUserClient{
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxUserService.class);
    private final CxAuthClient authClient;
    private final CxLegacyService cxLegacyService;
    private final CxProperties cxProperties;

    public CxUserService(CxAuthClient authClient, CxLegacyService cxLegacyService, CxProperties cxProperties) {
        this.authClient = authClient;
        this.cxLegacyService = cxLegacyService;
        this.cxProperties = cxProperties;
    }


    @Override
    public List<CxUser> getUsers() throws CheckmarxException {
        return null;
    }

    @Override
    public CxUser getUser(Integer id) throws CheckmarxException {
        return null;
    }

    @Override
    public void addUser(CxUser user) throws CheckmarxException {

    }

    @Override
    public void updateUser(CxUser user) throws CheckmarxException {

    }

    @Override
    public void deleteUser(Integer id) throws CheckmarxException {

    }
}
