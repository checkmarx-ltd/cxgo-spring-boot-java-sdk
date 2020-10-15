package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxConfig;
import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.CxScanParams;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.od.Scan;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.exception.InvalidCredentialsException;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@Import(CxConfig.class)
@SpringBootTest
public class CxServiceIT {

    @Autowired
    private CxProperties properties;
    @Autowired
    private CxService service;
    @Autowired
    private CxRepoFileService repoFileService;
    @Autowired
    private CxAuthService authService;

    @Test
    public void Login() {
        try {
            HttpHeaders token = authService.createAuthHeaders();
            assertNotNull(token);
        }catch (InvalidCredentialsException e){
            fail("Unexpected InvalidCredentialsException");
        }
    }

    @Test
    public void GetTeams() {
        try {
            String teamId = service.getTeamId(properties.getTeam());
            assertNotNull(teamId);
        }catch (CheckmarxException e){
            fail("Unexpected CheckmarxException");
        }
    }

    @Test
    public void GetProject() {
        try {
            String teamId = service.getTeamId(properties.getTeam());
            Integer projId = service.getProjectId(teamId, "CircleCI");
            if(projId == -1){
                projId = service.createProject(teamId, "CircleCI");
            }
            assertNotNull(projId);
        }catch (CheckmarxException e){
            fail("Unexpected CheckmarxException");
        }
    }

    @Test
    public void GitClone() throws CheckmarxException {
        CxScanParams params = new CxScanParams();
        params.setProjectName("CircleCI");
        params.setTeamId("1");
        params.setGitUrl("https://github.com/Custodela/Riches.git");
        params.setBranch("refs/heads/master");
        params.setSourceType(CxScanParams.Type.GIT);
        String zipFilePath = repoFileService.prepareRepoFile(params);
        assertTrue("Zip file path is empty.", StringUtils.isNotEmpty(zipFilePath));
    }

    @Test
    public void CompleteScanFlow() throws CheckmarxException {
        String teamId = service.getTeamId(properties.getTeam());
        Integer projectId = service.getProjectId(teamId, "CircleCI");
        CxScanParams params = new CxScanParams();
        params.setProjectName("CircleCI");
        params.setTeamId(teamId);
        params.setProjectId(projectId);
        params.setGitUrl("https://github.com/Custodela/Riches.git");
        params.setBranch("refs/heads/master");
        params.setSourceType(CxScanParams.Type.GIT);
        //run the scan and wait for it to finish
        Integer x = service.createScan(params, "CxFlow Scan");
        service.waitForScanCompletion(x);
        FilterConfiguration filterConfiguration = FilterConfiguration.fromSimpleFilters(
                Collections.singletonList(new Filter(Filter.Type.SEVERITY, "High")));
        //generate the results
        ScanResults results = service.getReportContentByScanId(x, filterConfiguration);
        assertNotNull(results);
    }

}