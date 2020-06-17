package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.cx.CxAuthResponse;
import com.checkmarx.sdk.dto.cx.CxScanParams;
import com.checkmarx.sdk.dto.od.*;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class CxServiceTest {
    private CxService cxService;

    @Mock
    private CxProperties cxProperties;

    @Mock
    private CxScanParams cxScanParams;

    @Mock
    private CxAuthClient authClient;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CxRepoFileService cxRepoFileService;

    private String odScanCreateDataID;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void init() throws CheckmarxException, URISyntaxException {
        String url = "https://www.google.ca/";
        String teamID = "teamID123";
        String branch = "test-branch";
        String projectName = "projectName123";
        String clonePath = "C:\\test";
        int projectIDInt = 123;
        odScanCreateDataID = "123";
        String repoFile = "C:\\test";
        String scanPreset = "scanPreset123";

        HttpHeaders httpHeaders = new HttpHeaders();

        OdProjectListDataItem item = new OdProjectListDataItem();
        item.setName(projectName);
        item.setId(1);

        List<OdProjectListDataItem> items = Lists.newArrayList(item);

        OdProjectListData data = new OdProjectListData();
        data.setItems(items);

        OdProjectList projectList = new OdProjectList();
        projectList.setData(data);

        OdScanCreateData createData = new OdScanCreateData();
        createData.setId(odScanCreateDataID);
        OdScanCreate odScanCreate = new OdScanCreate();
        odScanCreate.setData(createData);

        OdScanFileUploadFields fields = new OdScanFileUploadFields();

        OdScanFileUploadData uploadData = new OdScanFileUploadData();
        uploadData.setFields(fields);
        uploadData.setId( url );

        OdScanFileUpload odScanFileUpload = new OdScanFileUpload();
        odScanFileUpload.setData(uploadData);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(new URI(url));

        OdProjectListDataItem itemListData = new OdProjectListDataItem();
        itemListData.setName(projectName);
        itemListData.setId(1);

        List<OdProjectListDataItem> odProjectListDataItems = new ArrayList<>();
        odProjectListDataItems.add(itemListData);

        OdProjectListData odProjectListData = new OdProjectListData();
        odProjectListData.setTotalCount(2l);
        odProjectListData.setItems(odProjectListDataItems);

        OdProjectList odProjectList = new OdProjectList();
        odProjectList.setData(odProjectListData);

        ResponseEntity<OdProjectList> response = new ResponseEntity<OdProjectList>(projectList, HttpStatus.OK);
        ResponseEntity<OdScanCreate> createResp = new ResponseEntity<OdScanCreate>(odScanCreate, HttpStatus.OK);
        ResponseEntity<OdScanFileUpload> uploadResp = new ResponseEntity<OdScanFileUpload>(odScanFileUpload, HttpStatus.OK);
        ResponseEntity<String> s3Resp = new ResponseEntity<String>(headers, HttpStatus.OK);
        ResponseEntity<OdScanTriggerResult> triggerResp = new ResponseEntity<OdScanTriggerResult>( HttpStatus.OK);
        ResponseEntity<OdProjectList> odProjectListResp = new ResponseEntity<OdProjectList>(odProjectList, HttpStatus.OK);

        cxService = new CxService(authClient, cxProperties, restTemplate);
        cxService.setCxRepoFileService( cxRepoFileService );

        // prepare data
        when(cxProperties.getUrl()).thenReturn(url);
        when(cxScanParams.getTeamId()).thenReturn(teamID);
        when(cxScanParams.getProjectName()).thenReturn(projectName);
        when(cxScanParams.getProjectId()).thenReturn(projectIDInt);
        when(cxScanParams.getGitUrl()).thenReturn(url);
        when(cxScanParams.getBranch()).thenReturn(branch);
        //when(cxProperties.getGitClonePath()).thenReturn(clonePath);
        when(cxProperties.getScanPreset()).thenReturn(scanPreset);

        when(authClient.createAuthHeaders()).thenReturn(httpHeaders);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(OdProjectList.class), anyString(), anyInt(), anyInt())).thenReturn(odProjectListResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(OdScanCreate.class))).thenReturn(createResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(OdScanFileUpload.class))).thenReturn(uploadResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class))).thenReturn(s3Resp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(OdScanTriggerResult.class))).thenReturn(triggerResp);

        when(cxRepoFileService.prepareRepoFile(anyString(), anyString())).thenReturn(repoFile);
    }

    @Test
    public void createScan_withProjectID_shouldBeSuccess() throws CheckmarxException {
        int result = cxService.createScan(cxScanParams, "comment123");
        assertEquals(Integer.parseInt(odScanCreateDataID), result);
    }

    @Test(expected = RestClientException.class)
    public void createScan_withProjectID_throwCreateScanError() throws CheckmarxException {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(OdScanCreate.class)))
                .thenThrow(RestClientException.class);
        cxService.createScan(cxScanParams, "test comment");
    }

    @Test(expected = RestClientException.class)
    public void createScan_withProjectID_throwScanTriggerResultError() throws CheckmarxException {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(OdScanTriggerResult.class)))
                .thenThrow(RestClientException.class);
        cxService.createScan(cxScanParams, "test comment");
    }

    @Test
    public void getScanStatus_withValidParams_expectsScanStatus(){
        int projectId = 123;
        int scanId = 2;

        OdScanListDataItem i = new OdScanListDataItem();
        i.setId(2);
        i.setStatus("Done");

        List<OdScanListDataItem> items = new ArrayList<>();
        items.add( i );

        OdScanListData data = new OdScanListData();
        data.setTotalCount(5);
        data.setItems(items);

        OdScanList odScanList = new OdScanList();
        odScanList.setData(data);

        ResponseEntity<OdScanList> odScanListResp = new ResponseEntity<OdScanList>(odScanList, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(OdScanList.class), anyInt(), anyInt(), anyInt())).thenReturn(odScanListResp);

        int status = cxService.getScanStatus(projectId, scanId);
        assertEquals(7, status);
    }

    @Test
    public void waitForScanCompletion_withValidScanID_shouldSuccess() throws CheckmarxException {
        int scanId = cxService.createScan(cxScanParams, "comment123");
        OdScanListDataItem i = new OdScanListDataItem();
        i.setId(scanId);
        i.setStatus("Done");

        List<OdScanListDataItem> items = new ArrayList<>();
        items.add( i );

        OdScanListData data = new OdScanListData();
        data.setTotalCount(5);
        data.setItems(items);

        OdScanList odScanList = new OdScanList();
        odScanList.setData(data);

        ResponseEntity<OdScanList> response = new ResponseEntity<OdScanList>(odScanList, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(OdScanList.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(response);

        cxService.waitForScanCompletion(scanId);
    }

    @Test
    public void getProjectId_withOwnerIdName_shouldReturnID(){
        String ownerId = "owner123";
        String name    = "projectName123";
        int projectID = cxService.getProjectId(ownerId, name);
        assertEquals(1, projectID);
    }

    @Test
    public void createTeam_withParentName_returnsNewString() throws CheckmarxException {
        String parentID = "parent123";
        String team     = "test-team";
        String badID    = "bad123";

        OdApplicationCreateData data = new OdApplicationCreateData();
        data.setBaId(badID);

        OdApplicationCreate app = new OdApplicationCreate();
        app.setData(data);

        ResponseEntity<OdApplicationCreate> response = new ResponseEntity<OdApplicationCreate>(app, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(OdApplicationCreate.class)))
                .thenReturn(response);

        String responseBadID = cxService.createTeam(parentID, team);
        assertEquals(badID, responseBadID);
    }
}
