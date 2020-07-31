package com.checkmarx.cucumber.component.report;

import com.checkmarx.sdk.CheckmarxSdkApplication;
import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.CxScanParams;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.od.*;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.service.*;
//import com.checkmarx.sdk.test.CxConfigMock;
import com.google.common.collect.Lists;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = { CxConfigMock.class, CheckmarxSdkApplication.class })
public class GenerateReportComponentSteps {

    private final CxAuthClient cxAuthClient;
    private final RestTemplate restTemplate;
    private final CxProperties cxProperties;
    private final CxRepoFileService cxRepoFileService;
    private CxService cxService;

    private String odScanCreateDataID;
    private CxScanParams cxScanParams;
    private int testScanID;
    private int expectedScanID;
    private ScanResults expectedScanResult;

    public GenerateReportComponentSteps(CxAuthClient cxAuthClient, RestTemplate restTemplate,
                              CxProperties cxProperties, CxScanParams cxScanParams,
                              CxRepoFileService cxRepoFileService) throws CheckmarxException, URISyntaxException {
        this.cxScanParams = cxScanParams;
        this.cxAuthClient = cxAuthClient;
        this.restTemplate = restTemplate;
        this.cxProperties = cxProperties;
        this.cxRepoFileService = cxRepoFileService;
    }

    @Given("Mocked CxService environment with mocked services data")
    public void cxServiceIsMocked() throws CheckmarxException, URISyntaxException {
        String url = "https://www.google.ca/";
        String teamID = "teamID123";
        String projectName = "projectName123";
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

        LinkedHashMap<String, ArrayList<Object>> tree = new LinkedHashMap<String, ArrayList<Object>>();
        tree.put("tree", Lists.newArrayList());
        tree.put("id", Lists.newArrayList(1));
        tree.put("title", Lists.newArrayList("title"));
        tree.put("children", Lists.newArrayList());

        OdNavigationTree odNavigationTree = new OdNavigationTree();
        odNavigationTree.setAdditionalProperty("data", tree);

        List<OdScanQueryItem> qItems = Lists.newArrayList();

        OdScanQueryData odScanQueryData = new OdScanQueryData();
        odScanQueryData.setId(qItems);

        OdScanQueries odScanQueries = new OdScanQueries();
        odScanQueries.setData(odScanQueryData);

        ResponseEntity<OdProjectList> response = new ResponseEntity<OdProjectList>(projectList, HttpStatus.OK);
        ResponseEntity<OdScanCreate> createResp = new ResponseEntity<OdScanCreate>(odScanCreate, HttpStatus.OK);
        ResponseEntity<OdScanFileUpload> uploadResp = new ResponseEntity<OdScanFileUpload>(odScanFileUpload, HttpStatus.OK);
        ResponseEntity<String> s3Resp = new ResponseEntity<String>(headers, HttpStatus.OK);
        ResponseEntity<OdScanTriggerResult> triggerResp = new ResponseEntity<OdScanTriggerResult>( HttpStatus.OK);
        ResponseEntity<OdProjectList> odProjectListResp = new ResponseEntity<OdProjectList>(odProjectList, HttpStatus.OK);
        ResponseEntity<OdNavigationTree> odNavigationTreeResp = new ResponseEntity<OdNavigationTree>(odNavigationTree, HttpStatus.OK);
        ResponseEntity<OdScanQueries> odScanQueriesResp = new ResponseEntity<OdScanQueries>(odScanQueries, HttpStatus.OK);

        // prepare data
        when(cxProperties.getUrl()).thenReturn(url);
        when(cxScanParams.getTeamId()).thenReturn(teamID);
        when(cxScanParams.getProjectName()).thenReturn(projectName);
        when(cxScanParams.getProjectId()).thenReturn(projectIDInt);
        when(cxScanParams.getSourceType()).thenReturn(CxScanParams.Type.FILE);
        when(cxScanParams.getFilePath()).thenReturn(repoFile);
        when(cxScanParams.getTeamName()).thenReturn("team\\team11");
        when(cxProperties.getScanPreset()).thenReturn(scanPreset);
        when(cxAuthClient.createAuthHeaders()).thenReturn(httpHeaders);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(OdProjectList.class), anyString(), anyInt(), anyInt())).thenReturn(odProjectListResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(OdScanCreate.class))).thenReturn(createResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(OdScanFileUpload.class))).thenReturn(uploadResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class))).thenReturn(s3Resp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(OdScanTriggerResult.class))).thenReturn(triggerResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(),  eq(OdNavigationTree.class))).thenReturn(odNavigationTreeResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(),  eq(OdScanQueries.class), any(), any())).thenReturn(odScanQueriesResp);

        when(cxRepoFileService.prepareRepoFile(any())).thenReturn(repoFile);
        when(cxProperties.getTeam()).thenReturn("team\\team11");
        when(cxProperties.getPortalUrl()).thenReturn("team\\team11");
    }

    @When("generate report and get report content from scan with {int}")
    public void create_scan_with(int expectedScanID) throws CheckmarxException  {
        this.expectedScanID = expectedScanID;
        cxService = new CxService(cxAuthClient, cxProperties, restTemplate, new FilterValidatorImpl());
        testScanID = cxService.createScan(cxScanParams, "comment");
        this.expectedScanResult = cxService.getReportContentByScanId(testScanID, FilterConfiguration.builder().build());
    }

    @Then("we should see the scan result ID")
    public void verifyReport() throws CheckmarxException {
        assertEquals(testScanID, expectedScanID);
        assertNotNull(this.expectedScanResult);
        assertEquals(this.expectedScanResult.getProjectId(), String.valueOf(expectedScanID));
    }
}
