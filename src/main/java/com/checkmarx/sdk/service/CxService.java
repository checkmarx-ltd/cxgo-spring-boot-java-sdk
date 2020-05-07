package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.*;
import com.checkmarx.sdk.dto.od.*;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import sun.text.resources.FormatData;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxService implements CxClient{
    private static final String UNKNOWN = "-1";
    /*
    report statuses - there are only 2:
    InProcess (1)
    Created (2)
    */
    public static final Integer REPORT_STATUS_CREATED = 2;
    private static final Map<String, Integer> STATUS_MAP = ImmutableMap.of(
            "TO VERIFY", 0,
            "CONFIRMED", 2,
            "URGENT", 3,
            "PROPOSED NOT EXPLOITABLE",4
    );
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxService.class);

    //
    /// Rest API endpoints
    //
    private static final String TEAMS = "/business-units/business-units";
    private static final String CREATE_SCAN = "/scans/scans";
    private static final String UPLOADS_SCAN_FILE = "/files/files/upload-zip";
    private static final String TRIGGER_SCAN = "/scans/scan";

    private final CxProperties cxProperties;
    private final CxAuthClient authClient;
    private final RestTemplate restTemplate;

    public CxService(CxAuthClient authClient, CxProperties cxProperties, @Qualifier("cxRestTemplate") RestTemplate restTemplate) {
        this.authClient = authClient;
        this.cxProperties = cxProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public Integer createScan(CxScanParams params, String comment) throws CheckmarxException {
        //TODO: jeffa, HACK remove this!!!
        params.setProjectId(10008);
        //
        /// First, the scan needs to be started
        //
        OdScan scan = OdScan.builder()
                .projectId(params.getProjectId())
                .build();
        log.info("Sending scan to CxOD for projectID {}.", params.getProjectId());
        HttpHeaders headers = authClient.createAuthHeaders();
        HttpEntity httpEntity = new HttpEntity<>(scan, headers);
        ResponseEntity<OdScanCreate> createResp = restTemplate.exchange(
                cxProperties.getUrl().concat(CREATE_SCAN),
                HttpMethod.PUT,
                httpEntity,
                OdScanCreate.class);
        OdScanCreate scanCreate = createResp.getBody();
        String scanId = scanCreate.getData().getId();
        log.info("CxOD started scan with scanId {}.", scanId);
        //
        /// Next, the repo to be scanned is uploaded to amazon bucket
        //
        log.info("CxOD Uploading Scan file {}.", scanId);
        OdFileUpload fileUpload = OdFileUpload.builder()
                .projectId(params.getProjectId().toString(), scanId, "junkstuff")
                .build();
        httpEntity = new HttpEntity<>(fileUpload, headers);
        ResponseEntity<OdScanFileUpload> uploadResp = restTemplate.exchange(
                cxProperties.getUrl().concat(UPLOADS_SCAN_FILE),
                HttpMethod.POST,
                httpEntity,
                OdScanFileUpload.class);
        OdScanFileUpload scanUpload = uploadResp.getBody();
        OdScanFileUploadData data = scanUpload.getData();
        OdScanFileUploadFields s3Fields = data.getFields();
        String bucketURL = data.getUrl();
        //String s3Key = s3Fields.getKey();
        // Now upload the file to the bucket
        File test = new File("C:\\Users\\JeffA\\Downloads\\testProj.zip");
        String s3FilePath = postS3File(bucketURL, "testProj.zip", test, s3Fields);

        //s3FilePath = "https://s3.amazonaws.com/cx-customers-ppe/cx-customer-71/cx-project-10008/SAST/cx-scan-10105/sources.zip";
        //
        /// Finally the scan is kicked off
        //
        log.info("CxOD Triggering the scan {}.", scanId);
        List<String> typeIds = new LinkedList<>();
        // TODO: jeffa, this needs to be updated to pick the correct presets
        typeIds.add("9");
        OdScanTrigger scanTrigger = OdScanTrigger.builder()
                .projectId(params.getProjectId().toString(), scanId, s3FilePath, typeIds)
                .build();
        httpEntity = new HttpEntity<>(scanTrigger, headers);
        ResponseEntity<OdScanTriggerResult> triggerResp = restTemplate.exchange(
                cxProperties.getUrl().concat(TRIGGER_SCAN),
                HttpMethod.POST,
                httpEntity,
                OdScanTriggerResult.class);
        OdScanTriggerResult triggerResult = triggerResp.getBody();
        return -1;

    }

    private String postS3File(String targetURL, String filename, File file, OdScanFileUploadFields s3Fields) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("key", s3Fields.getKey());
            body.add("bucket", s3Fields.getBucket());
            body.add("X-Amz-Algorithm", s3Fields.getX_Amz_Algorithm());
            body.add("X-Amz-Credential", s3Fields.getX_Amz_Credential());
            body.add("X-Amz-Date", s3Fields.getX_Amz_Date());
            body.add("X-Amz-Security-Token", s3Fields.getX_Amz_Security_Token());
            body.add("Policy", s3Fields.getPolicy());
            body.add("X-Amz-Signature", s3Fields.getX_Amz_Signature());
            // Add the file to upload
            MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
            ContentDisposition contentDisposition = ContentDisposition
                    .builder("form-data")
                    .name("file")
                    .filename(filename)
                    .build();
            //fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
            //byte[] fileContent = Files.readAllBytes(file.toPath());
            //HttpEntity<byte[]> fileEntity = new HttpEntity<>(fileContent, fileMap);
            FileSystemResource fsr = new FileSystemResource(file);
            body.add("file", fsr);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> s3Resp = restTemplate.exchange(
                    targetURL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            URI s3FilePath = s3Resp.getHeaders().getLocation();
            return java.net.URLDecoder.decode(s3FilePath.toString(), StandardCharsets.UTF_8.name());
        } catch(HttpClientErrorException e) {
            log.info("CxOD error uploading file.");
            e.printStackTrace();
        } catch(UnsupportedEncodingException e) {
            log.info("CxOD code not decode S3 file path.");
            e.printStackTrace();
        }
        return null;
    }

    private void oldPost() {
        /*
        File test = new File("C:\\Users\\JeffA\\Downloads\\testProj.zip");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("key", s3Key);
        body.add("bucket", s3Fields.getBucket());
        body.add("X-Amz-Algorithm", s3Fields.getX_Amz_Algorithm());
        body.add("X-Amz-Credential", s3Fields.getX_Amz_Credential());
        body.add("X-Amz-Date", s3Fields.getX_Amz_Date());
        body.add("X-Amz-Security-Token", s3Fields.getX_Amz_Security_Token());
        body.add("Policy", s3Fields.getPolicy());
        body.add("X-Amz-Signature", s3Fields.getX_Amz_Signature());
        body.add("file", test);
        HttpHeaders amzHeaders = new HttpHeaders();
        amzHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        httpEntity = new HttpEntity<>(body, amzHeaders);
        ResponseEntity<String> s3Resp = restTemplate.exchange(
                bucketURL,
                HttpMethod.POST,
                httpEntity,
                String.class);
        URI s3FilePath = s3Resp.getHeaders().getLocation();
        */
    }

    /**
     * Finds the Business Unit ID
     *
     * NOTE: this has a potential non-deterministic state that could occur if separate BU's have the same name.
     *
     * @param teamPath
     * @return the Business Unit ID or -1
     * @throws CheckmarxException
     */
    @Override
    public String getTeamId(String teamPath) throws CheckmarxException {
        try {
            String [] buTokens = teamPath.split(Pattern.quote("\\"));
            String buName = buTokens[buTokens.length -1];
            List<BusinessUnitListEntry> businessUnits = getBusinessUnits();
            if(businessUnits == null) {
                throw new CheckmarxException("Error obtaining Business Unit Id");
            }
            for(BusinessUnitListEntry businessUnit : businessUnits) {
                if(businessUnit.getName().equals(buName)) {
                    log.info("Found team {} with ID {}", teamPath, businessUnit.getId());
                    return businessUnit.getId().toString();
                }
            }
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving Teams");
            log.error(ExceptionUtils.getStackTrace(e));
        }
        log.info("No Business Unit was found for {}", teamPath);
        return UNKNOWN;
    }

    private List<BusinessUnitListEntry> getBusinessUnits() throws CheckmarxException {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.info("Retrieving OD Business Units");
            ResponseEntity<OdBusinessUnitList> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(TEAMS),
                    HttpMethod.GET,
                    httpEntity,
                    OdBusinessUnitList.class);
            OdBusinessUnitList businessUnits = response.getBody();
            return businessUnits.getData().getItems();
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving Business units.");
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error retrieving Business Units.");
        }
    }

    @Override
    public String createTeam(String parentID, String teamName) throws CheckmarxException {
        log.info("Create OD Business Unit {} with parentID {}", teamName, parentID);
        HttpEntity httpEntity = new HttpEntity<>(
                                        getJSONCreateBUReq(parentID, teamName),
                                        authClient.createAuthHeaders());
        ResponseEntity<OdBusinessUnitCreate> response = restTemplate.exchange(
                cxProperties.getUrl().concat(TEAMS),
                HttpMethod.PUT,
                httpEntity,
                OdBusinessUnitCreate.class);
        OdBusinessUnitCreate buCreate = response.getBody();
        return buCreate.getData().getId();
    }

    /**
     * Generate JSON http request body for creating new CxOD Business Unit
     *
     * @return String representation of the token
     */
    private String getJSONCreateBUReq(String parentID, String name) {
        JSONObject requestBody = new JSONObject();
        JSONObject createBody = new JSONObject();
        try {
            createBody.put("buParentId", parentID);
            createBody.put("buName", name);
            requestBody.put("businessUnit", createBody);
        } catch (JSONException e) {
            log.error("Error generating JSON BU create Request object - JSON object will be empty");
        }
        return requestBody.toString();
    }

    //
    /// I think things below here should be removed the public interface. They are specific
    /// Cx SAST.
    //
    @Override
    public Integer getLastScanId(Integer projectId) {
        return null;
    }

    @Override
    public JSONObject getScanData(String scanId) {
        return null;
    }

    @Override
    public LocalDateTime getLastScanDate(Integer projectId) {
        return null;
    }

    @Override
    public Integer getScanStatus(Integer scanId) {
        return null;
    }

    @Override
    public Integer createScanReport(Integer scanId) {
        return null;
    }

    @Override
    public Integer getReportStatus(Integer reportId) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults getReportContentByScanId(Integer scanId, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults getReportContent(Integer reportId, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public Map<String, String> getCustomFields(Integer projectId) {
        return null;
    }

    @Override
    public ScanResults getReportContent(File file, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults getOsaReportContent(File vulnsFile, File libsFile, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public String getIssueDescription(Long scanId, Long pathId) {
        return null;
    }

    @Override
    public Integer createProject(String ownerId, String name) {
        return null;
    }

    @Override
    public void deleteProject(Integer projectId) {

    }

    @Override
    public List<CxProject> getProjects() throws CheckmarxException {
        return null;
    }

    @Override
    public List<CxProject> getProjects(String teamId) throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getProjectId(String ownerId, String name) {
        return null;
    }

    @Override
    public CxProject getProject(Integer projectId) {
        return null;
    }

    @Override
    public boolean scanExists(Integer projectId) {
        return false;
    }

    @Override
    public Integer createScanSetting(Integer projectId, Integer presetId, Integer engineConfigId) {
        return null;
    }

    @Override
    public String getScanSetting(Integer projectId) {
        return null;
    }

    @Override
    public String getPresetName(Integer presetId) {
        return null;
    }

    @Override
    public Integer getProjectPresetId(Integer projectId) {
        return null;
    }

    @Override
    public void setProjectRepositoryDetails(Integer projectId, String gitUrl, String branch) throws CheckmarxException {

    }

    @Override
    public void updateProjectDetails(CxProject project) throws CheckmarxException {

    }

    @Override
    public void uploadProjectSource(Integer projectId, File file) throws CheckmarxException {

    }

    @Override
    public void setProjectExcludeDetails(Integer projectId, List<String> excludeFolders, List<String> excludeFiles) {

    }

    @Override
    public Integer getLdapTeamMapId(Integer ldapServerId, String teamId, String ldapGroupDn) throws CheckmarxException {
        return null;
    }

    @Override
    public List<CxTeam> getTeams() throws CheckmarxException {
        return null;
    }

    @Override
    public void mapTeamLdap(Integer ldapServerId, String teamId, String teamName, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public List<CxTeamLdap> getTeamLdap(Integer ldapServerId) throws CheckmarxException {
        return null;
    }

    @Override
    public void removeTeamLdap(Integer ldapServerId, String teamId, String teamName, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public List<CxRole> getRoles() throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getRoleId(String roleName) throws CheckmarxException {
        return null;
    }

    @Override
    public List<CxRoleLdap> getRoleLdap(Integer ldapServerId) throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getLdapRoleMapId(Integer ldapServerId, Integer roleId, String ldapGroupDn) throws CheckmarxException {
        return null;
    }

    @Override
    public void mapRoleLdap(Integer ldapServerId, Integer roleId, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public void removeRoleLdap(Integer roleMapId) throws CheckmarxException {

    }

    @Override
    public void removeRoleLdap(Integer ldapServerId, Integer roleId, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public void mapTeamLdapWS(Integer ldapServerId, String teamId, String teamName, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public void removeTeamLdapWS(Integer ldapServerId, String teamId, String teamName, String ldapGroupDn) throws CheckmarxException {

    }

    @Override
    public String createTeamWS(String parentTeamId, String teamName) throws CheckmarxException {
        return null;
    }

    @Override
    public void moveTeam(String teamId, String newParentTeamId) throws CheckmarxException {

    }

    @Override
    public void renameTeam(String teamId, String newTeamName) throws CheckmarxException {

    }

    @Override
    public void deleteTeam(String teamId) throws CheckmarxException {

    }

    @Override
    public String getTeamId(String parentTeamId, String teamName) throws CheckmarxException {
        return null;
    }

    @Override
    public void deleteTeamWS(String teamId) throws CheckmarxException {

    }

    @Override
    public Integer getScanConfiguration(String configuration) throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getPresetId(String preset) throws CheckmarxException {
        return null;
    }

    @Override
    public CxScanSummary getScanSummaryByScanId(Integer scanId) throws CheckmarxException {
        return null;
    }

    @Override
    public CxScanSummary getScanSummary(Integer projectId) throws CheckmarxException {
        return null;
    }

    @Override
    public CxScanSummary getScanSummary(String teamName, String projectName) throws CheckmarxException {
        return null;
    }

    @Override
    public void waitForScanCompletion(Integer scanId) throws CheckmarxException {

    }

    @Override
    public void deleteScan(Integer scanId) throws CheckmarxException {

    }

    @Override
    public ScanResults getLatestScanResults(String teamName, String projectName, List<Filter> filters) throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getLdapServerId(String serverName) throws CheckmarxException {
        return null;
    }
}
