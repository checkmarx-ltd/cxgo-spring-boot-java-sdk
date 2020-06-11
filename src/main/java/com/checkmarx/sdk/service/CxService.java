package com.checkmarx.sdk.service;

import com.checkmarx.sdk.utils.ZipUtils;
import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.*;
import com.checkmarx.sdk.dto.od.*;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxService implements CxClient{
    private static final String UNKNOWN = "-1";

    private static final Integer SCAN_STATUS_FINISHED = 7;
    private static final Integer SCAN_STATUS_CANCELED = 8;
    private static final Integer SCAN_STATUS_FAILED = 9;

    //
    /// Scan Preset Definitions for reference
    //
    public static final Integer CXOD_MOBILE_NATIVE = 1;
    public static final Integer CXOD_MOBILE_WEB_BASED = 2;
    public static final Integer CXOD_DESKTOP_NATIVE = 3;
    public static final Integer CXOD_DESKTOP_WEB = 4;
    public static final Integer CXOD_API = 5;
    public static final Integer CXOD_FRONTEND = 6;
    public static final Integer CXOD_BACKEND = 7;
    public static final Integer CXOD_LAMBDA = 8;
    public static final Integer CXOD_CLI = 9;
    public static final Integer CXOD_SERVICE = 10;
    public static final Integer CXOD_SMART_DEVICE = 11;
    public static final Integer CXOD_OTHER = 12;


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
    private static final String SCAN_QUERIES = "/projects/projects/{project_id}/scans/{scan_id}/queries";
    private static final String SCAN_RESULTS_ENCODED = "/results/results?criteria=%7B%22filters%22%3A%5B%5D%2C%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%2C%7B%22key%22%3A%22scanId%22%2C%22value%22%3A%22{scan_id}%22%7D%2C%7B%22key%22%3A%22queryId%22%2C%22value%22%3A%22{query_id}%22%7D%5D%2C%22sorting%22%3A%5B%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A0%2C%22pageSize%22%3A50%7D%7D";
    private static final String SCAN_RESULT_NODES_ENCODED = "/nodes/nodes?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%2C%7B%22key%22%3A%22scanId%22%2C%22value%22%3A%22{scan_id}%22%7D%2C%7B%22key%22%3A%22resultId%22%2C%22value%22%3A%22{result_id}%22%7D%5D%2C%22sorting%22%3A%5B%5D%7D";
    private static final String SCAN_FILE = "/projects/projects/{project_id}/scans/{scan_id}/files?filePath={file_path};";
    private static final String CREATE_APPLICATION = "/applications/applications";
    private static final String CREATE_PROJECT = "/projects/projects";
    public static final String GET_PROJECTS = "/projects/projects?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22applicationId%22%2C%22value%22%3A%22{app_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A0%2C%22pageSize%22%3A50%7D%2C%22sorting%22%3A%5B%5D%7D";
    private static final String GET_SCAN_STATUS = "/scans/scans?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A0%2C%22pageSize%22%3A50%7D%2C%22sorting%22%3A%5B%5D%7D";

    //
    /// CxOD required extra information for API calls not used by the SAST SDK. This
    /// data structure is used to capture that information as CxService calls are made
    /// during scan requests. This information is tracked using the current CxOD scan ID
    /// as the key. The 'scanIdMap' and 'scanProbeMap' have to be constructed using
    /// different keys because CxService is starting the processes differently; with new
    /// scans we start with asking CxOD for a scanID, and with requests for previous
    /// results we are starting with project information and trying to find the last
    /// scanID.
    //
    private static Map<String, CxScanParams> scanIdMap = new HashMap();
    //
    /// This was used for /scanresults API calls to avoid modifying CxFlow. This
    /// captures information at key points as CxService API calls are made so
    /// that it will be required later when needed using the team name as the key.
    //
    private static List<CxScanParams> scanProbeMap = new LinkedList<>();

    private final CxProperties cxProperties;
    private final CxAuthClient authClient;
    private final RestTemplate restTemplate;
    private Map<String, Object> codeCache = new HashMap<String, Object>();
    private CxRepoFileService cxRepoFileService;

    public CxService(CxAuthClient authClient, CxProperties cxProperties, @Qualifier("cxRestTemplate") RestTemplate restTemplate) {
        this.authClient = authClient;
        this.cxProperties = cxProperties;
        this.restTemplate = restTemplate;
    }

    private String createApplication(String appName, String appDesc, String baBuId) {
        log.info("Creating new CxOD application {}.", appName);
        HttpEntity httpEntity = new HttpEntity<>(
                getJSONCreateAppReq(appName, appDesc, baBuId),
                authClient.createAuthHeaders());
        ResponseEntity<OdApplicationCreate> createResp = restTemplate.exchange(
                cxProperties.getUrl().concat(CREATE_APPLICATION),
                HttpMethod.PUT,
                httpEntity,
                OdApplicationCreate.class);
        OdApplicationCreate appCreate = createResp.getBody();
        return appCreate.getData().getBaId();
    }

    /**
     * Generate JSON http request body for creating new Application
     *
     * @return String representation of the process
     */
    private String getJSONCreateAppReq(String appName, String appDesc, String baBuId) {
        JSONObject requestBody = new JSONObject();
        JSONObject createBody = new JSONObject();
        try {
            createBody.put("baName", appName);
            createBody.put("description", appDesc);
            createBody.put("criticality", 5);
            createBody.put("baBuId", baBuId);
            requestBody.put("businessApplication", createBody);
        } catch (JSONException e) {
            log.error("Error generating JSON App create Request object - JSON object will be empty");
        }
        return requestBody.toString();
    }

    private String createCxODProject(String appId, String projectName) {
        log.info("Creating new CxOD project.");
        HttpEntity httpEntity = new HttpEntity<>(
                getJSONCreateProjectReq(appId, projectName),
                authClient.createAuthHeaders());
        ResponseEntity<OdProjectCreate> createResp = restTemplate.exchange(
                cxProperties.getUrl().concat(CREATE_PROJECT),
                HttpMethod.PUT,
                httpEntity,
                OdProjectCreate.class);
        OdProjectCreate appCreate = createResp.getBody();
        return appCreate.getData().getId();
    }

    /**
     * Generate JSON http request body for creating a new project
     *
     * @return String representation of the process
     */
    private String getJSONCreateProjectReq(String appId, String projectName) {
        JSONObject requestBody = new JSONObject();
        JSONObject createBody = new JSONObject();
        try {
            createBody.put("businessApplicationId", appId);
            createBody.put("name", projectName);
            createBody.put("description", "");
            String preset = cxProperties.getScanPreset();
            String [] presets = preset.split(",");
            createBody.put("typeIds", presets);
            createBody.put("criticality", 5);
            requestBody.put("project", createBody);
        } catch (JSONException e) {
            log.error("Error generating JSON Project create Request object - JSON object will be empty");
        }
        return requestBody.toString();
    }

    @Override
    public Integer createScan(CxScanParams params, String comment) throws CheckmarxException {
        //
        /// Create the project if it doesn't exist.
        //
        String appID = params.getTeamId();
        Integer projectID = getProjectId(appID, params.getProjectName());
        if(projectID == -1) {
            projectID = Integer.parseInt(createCxODProject(appID, params.getProjectName()));
        }
        params.setProjectId(projectID);
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

        // Now upload the file to the bucket
        // Lines commented out are hack for a local file for quick testing
        //File test = new File("C:\\Users\\JeffA\\Downloads\\testProj.zip");
        //File test = new File("C:\\Users\\JeffA\\Downloads\\dvna-master.zip");
        //File test = new File(prepareRepoFile(params.getGitUrl(), params.getBranch()));
        File test = new File(cxRepoFileService.prepareRepoFile(params.getGitUrl(), params.getBranch()));
        //String s3FilePath = postS3File(bucketURL, "testProj.zip", test, s3Fields);
        //String s3FilePath = postS3File(bucketURL, "archive.zip", test, s3Fields);
        //prepareRepoFile(params.getGitUrl(), params.getBranch());
        cxRepoFileService.prepareRepoFile(params.getGitUrl(), params.getBranch());

        // Now, upload the file to the bucket
        File archive = null;
        if(params.getSourceType() == CxScanParams.Type.FILE) {
            archive = new File(params.getFilePath());
        } else {
            archive = new File(cxRepoFileService.prepareRepoFile(params.getGitUrl(), params.getBranch()));
        }
        String s3FilePath = postS3File(bucketURL, "archive.zip", archive, s3Fields);
        FileSystemUtils.deleteRecursively(archive);

        //
        /// Finally the scan is kicked off
        //
        log.info("CxOD Triggering the scan {}.", scanId);
        String preset = cxProperties.getScanPreset();
        String []presets = preset.split(",");
        OdScanTrigger scanTrigger = OdScanTrigger.builder()
                .projectId(params.getProjectId().toString(), scanId, s3FilePath, presets)
                .build();
        httpEntity = new HttpEntity<>(scanTrigger, headers);
        ResponseEntity<OdScanTriggerResult> triggerResp = restTemplate.exchange(
                cxProperties.getUrl().concat(TRIGGER_SCAN),
                HttpMethod.POST,
                httpEntity,
                OdScanTriggerResult.class);
        // There is currently no use for the triggerResult!
        OdScanTriggerResult triggerResult = triggerResp.getBody();
        scanIdMap.put(scanId, params);
        return Integer.parseInt(scanId);
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

    /**
     * Searches the navigation tree for the Business Unit.
     *
     * @param teamPath
     * @return the Business Unit ID or -1
     * @throws CheckmarxException
     */
    @Override
    public String getTeamId(String teamPath) throws CheckmarxException {
        String []buTokens = teamPath.split(Pattern.quote("\\"));
        OdNavigationTree navTree = getNavigationTree();
        LinkedHashMap<String, ArrayList<Object>> navTreeData = (LinkedHashMap) navTree.getAdditionalProperties().get("data");
        ArrayList<LinkedHashMap<String, LinkedHashMap<String, Object>>> tree = (ArrayList) navTreeData.get("tree");
        int i = 1;
        String token = buTokens[i++];
        for(LinkedHashMap<String, LinkedHashMap<String, Object>> item : tree) {
            Object o = item.get("id");
            Integer id = (Integer)o;
            o = item.get("title");
            String title = (String)o;
            title = title.trim();
            o = item.get("children");
            ArrayList<Object> children = (ArrayList<Object>) o;
            if(title.equals(token)) {
                if(i == buTokens.length) {
                    CxScanParams csp = getScanProbeByTeam(id.toString());
                    csp.setTeamName(teamPath);
                    return id.toString();
                } else {
                    return searchTreeChildren(teamPath, buTokens, i, children);
                }
            }
        }
        return UNKNOWN;
    }

    private String searchTreeChildren(String teamPath, String []buTokens, int i, ArrayList<Object> children) {
        String token = buTokens[i++];
        for(Object item : children) {
            LinkedHashMap<String, Object> node = (LinkedHashMap<String, Object>)item;
            Object o = node.get("id");
            Integer id = (Integer) o;
            o = node.get("title");
            String title = (String)o;
            title = title.trim();
            o = node.get("children");
            ArrayList<Object> nodeChildren = (ArrayList<Object>)o;
            if(title.equals(token)) {
                if(i == buTokens.length) {
                    CxScanParams csp = getScanProbeByTeam(id.toString());
                    csp.setTeamName(teamPath);
                    return id.toString();
                } else {
                    return searchTreeChildren(teamPath, buTokens, i, nodeChildren);
                }
            }
        }
        return UNKNOWN;
    }

    private OdNavigationTree getNavigationTree() throws CheckmarxException {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.info("Retrieving OD Navigation Tree");
            ResponseEntity<OdNavigationTree> response = restTemplate.exchange(
                    cxProperties.getUrl().concat("/navigation-tree/navigation-tree"),
                    HttpMethod.GET,
                    httpEntity,
                    OdNavigationTree.class);
            OdNavigationTree tree = response.getBody();
            return tree;
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving the navigation tree.");
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error retrieving Business Units.");
        }
    }

    @Override
    public String createTeam(String parentID, String teamName) throws CheckmarxException {
        return createApplication(teamName, "Generated by CxFlow", parentID);
    }

    private String createCxODBusinessUnit(String parentID, String teamName) {
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

    @Override
    public ScanResults getReportContentByScanId(Integer scanId, List<Filter> filter) throws CheckmarxException {
        ScanResults.ScanResultsBuilder scanResults = ScanResults.builder();
        CxScanParams params = scanIdMap.get(scanId.toString());
        Integer projectId = params.getProjectId();
        String buId = getTeamId(cxProperties.getTeam());
        String appId = getTeamId(params.getTeamName());
        getScanQueries(scanResults, projectId, scanId, buId, appId, filter);
        scanResults.projectId(projectId.toString());
        String deepLink = cxProperties.getPortalUrl().concat("/scan/business-unit/%s/application/%s/project/%s/scans/%s");
        deepLink = String.format(deepLink, buId, appId, projectId, scanId);
        scanResults.link(deepLink);
        return scanResults.build();
    }

    private void getScanQueries(ScanResults.ScanResultsBuilder scanResults,
                                    Integer projectId,
                                    Integer scanId,
                                    String buId,
                                    String appId,
                                    List<Filter> filter) throws CheckmarxException {
        CxScanSummary scanSummary = new CxScanSummary();
        HttpEntity httpEntity = new HttpEntity<>(null, authClient.createAuthHeaders());
        ResponseEntity<OdScanQueries> response = restTemplate.exchange(
                cxProperties.getUrl().concat(SCAN_QUERIES),
                HttpMethod.GET,
                httpEntity,
                OdScanQueries.class,
                projectId,
                scanId
                );
        OdScanQueries scanQueries = response.getBody();
        List<ScanResults.XIssue> xIssueList = new ArrayList<>();
        for(OdScanQueryItem item : scanQueries.getData().getItems()) {
            for(OdScanQueryCategory vulnerability : item.getCategories()) {
                getScanResults(buId,
                                appId,
                                projectId,
                                scanId,
                                item.getLanguage(),
                                vulnerability,
                                scanSummary,
                                xIssueList,
                                filter);
            }
        }
        scanResults.scanSummary(scanSummary);
        scanResults.xIssues(xIssueList);
        Map<String, Object> additionalDetails = new HashMap();
        Map<String, Integer> scanDetails = new HashMap();
        scanDetails.put("high", scanSummary.getHighSeverity());
        scanDetails.put("medium", scanSummary.getMediumSeverity());
        scanDetails.put("low", scanSummary.getLowSeverity());
        additionalDetails.put("flow-summary", scanDetails);
        scanResults.additionalDetails(additionalDetails);
    }

    private void getScanResults(String buId,
                                    String appId,
                                    Integer projectId,
                                    Integer scanId,
                                    String language,
                                    OdScanQueryCategory vulnerability,
                                    CxScanSummary scanSummary,
                                    List<ScanResults.XIssue> xIssueList,
                                    List<Filter> filter) {
        HttpEntity httpEntity = new HttpEntity<>(null, authClient.createAuthHeaders());
        ResponseEntity<OdScanResults> response = restTemplate.exchange(
                cxProperties.getUrl().concat(SCAN_RESULTS_ENCODED),
                HttpMethod.GET,
                httpEntity,
                OdScanResults.class,
                projectId,
                scanId,
                vulnerability.getId()
        );
        OdScanResults scanResults = response.getBody();
        for(OdScanResultItem item : scanResults.getData().getItems()) {
            if(checkFilter(vulnerability, filter)) {
                String deepLink = cxProperties.getPortalUrl().concat("/scan/business-unit/%s/application/%s/project/%s/scans/%s");
                deepLink = String.format(deepLink, buId, appId, projectId, scanId);
                ScanResults.XIssue.XIssueBuilder xib = ScanResults.XIssue.builder();
                xib.similarityId(item.getSimilarityId());
                xib.file(item.getSourceFile());
                xib.link(deepLink);
                xib.language(language);
                xib.severity(vulnerability.getSeverity());
                xib.vulnerability(vulnerability.getTitle());
                String comment = item.getHasNotes() ? item.getNotes() : "";
                boolean falsePositive = (item.getState() == 2) ? true : false;
                Map<Integer, ScanResults.IssueDetails> issueDetailsList = new HashMap<>();
                Map<Integer, ScanResults.IssueDetails> detail = getScanResultDetails(projectId,
                                                                                        scanId,
                                                                                        item.getId(),
                                                                                        comment,
                                                                                        falsePositive);
                Integer []keys = new Integer[1];
                detail.keySet().toArray(keys);
                Integer loc = keys[0];
                ScanResults.IssueDetails issueDetail = detail.get(loc);
                issueDetailsList.put(loc, issueDetail);
                xib.details(issueDetailsList);
                ScanResults.XIssue issue = xib.build();
                if(!xIssueList.contains(issue)) {
                    updateIssueSummary(scanSummary, item);
                    xIssueList.add(issue);
                } else {
                    ScanResults.XIssue existingIssue = xIssueList.get(xIssueList.indexOf(issue));
                    existingIssue.getDetails().put(loc, issueDetail);
                }
            }
        }
    }

    private Map<Integer, ScanResults.IssueDetails> getScanResultDetails(Integer projectId,
                                                          Integer scanId,
                                                          Integer resultId,
                                                          String comment,
                                                          boolean falsePositive) {
        HttpEntity httpEntity = new HttpEntity<>(null, authClient.createAuthHeaders());
        ResponseEntity<OdScanNodes> response = restTemplate.exchange(
                cxProperties.getUrl().concat(SCAN_RESULT_NODES_ENCODED),
                HttpMethod.GET,
                httpEntity,
                OdScanNodes.class,
                projectId,
                scanId,
                resultId
        );
        OdScanNodes scanNodes = response.getBody();
        ScanResults.IssueDetails issueDetails = null;
        Map<Integer, ScanResults.IssueDetails> detail = new HashMap<>();
        if(scanNodes.getData().getItems().size() > 0) {
            OdScanNodeItem item = scanNodes.getData().getItems().get(0);
            String snippet = extractCodeSnippet(projectId, scanId, item.getLine(), item.getFile().getId());
            issueDetails = new ScanResults.IssueDetails()
                    .codeSnippet(snippet)
                    .comment(comment)
                    .falsePositive(falsePositive);
            detail.put(item.getLine(), issueDetails);
        }
        return detail;
    }

    private void updateIssueSummary(CxScanSummary scanSummary, OdScanResultItem vulnerability) {
        if(scanSummary.getLowSeverity() == null) scanSummary.setLowSeverity(0);
        if(scanSummary.getMediumSeverity() == null) scanSummary.setMediumSeverity(0);
        if(scanSummary.getHighSeverity() == null) scanSummary.setHighSeverity(0);
        if(vulnerability.getSeverity().equals("low")) {
            scanSummary.setLowSeverity(scanSummary.getLowSeverity() + 1);
        }
        if(vulnerability.getSeverity().equals("medium")) {
            scanSummary.setMediumSeverity(scanSummary.getMediumSeverity() + 1);
        }
        if(vulnerability.getSeverity().equals("high")) {
            scanSummary.setHighSeverity(scanSummary.getHighSeverity() + 1);
        }
    }

    /**
     * Check if the highlevel Query resultset meets the filter criteria
     *
     * @param q Issue containing results to filter
     * @param filters Filters to apply to results.
     * @return true if the Query meets filter criteria.
     */
    private boolean checkFilter(OdScanQueryCategory q, List<Filter> filters) {
        if(filters == null || filters.isEmpty()) {
            return true;
        }
        List<String> severity = new ArrayList<>();
        List<String> cwe = new ArrayList<>();
        List<String> category = new ArrayList<>();
        for(Filter f: filters) {
            Filter.Type type = f.getType();
            String value = f.getValue();
            if (type.equals(Filter.Type.SEVERITY)) {
                severity.add(value.toUpperCase(Locale.ROOT));
            } else if (type.equals(Filter.Type.TYPE)) {
                category.add(value.toUpperCase(Locale.ROOT));
            } else if (type.equals(Filter.Type.CWE)) {
                cwe.add(value.toUpperCase(Locale.ROOT));
            }
        }
        if(!severity.isEmpty() && !severity.contains(q.getSeverity().toUpperCase(Locale.ROOT))) {
            return false;
        }
        // TODO: CWE filter is disabled because the data doesn't currently exist
        //if (!cwe.isEmpty() && !cwe.contains(q.getMetadata().getCweId())) {
        //    return false;
        //}
        return category.isEmpty() || category.contains(q.getTitle().toUpperCase(Locale.ROOT));
    }

    /**
     * Fetches the source file and extract the code on the line with the error. This attempts to
     * cache downloaded source files in memory to try conserve network bandwidth.
     *
     * @param projectId project to get source from
     * @param scanId specific scan within project to pull source file from
     * @param filePath the path to the file int he code base
     * @return String containing the extracted source file
     */
    private String extractCodeSnippet(Integer projectId,
                                      Integer scanId,
                                      Integer lineNumber,
                                      String filePath) {
        // Does the cache already contain the source file?
        String sourceCode;
        if(codeCache.containsKey(filePath)) {
            sourceCode = (String)codeCache.get(filePath);
        } else {
            HttpEntity httpEntity = new HttpEntity<>(null, authClient.createAuthHeaders());
            ResponseEntity<OdScanFileResult> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN_FILE),
                    HttpMethod.GET,
                    httpEntity,
                    OdScanFileResult.class,
                    projectId,
                    scanId,
                    filePath
            );
            OdScanFileResult sfr = response.getBody();
            sourceCode = sfr.getData().getCode();
            codeCache.put(filePath, sourceCode);
        }
        //
        /// Now extract the code snippet to display
        //
        String codeLine = "NOT FOUND!";
        try {
            Reader code = new StringReader(sourceCode);
            BufferedReader codeReader = new BufferedReader(code);
            int curLine = 1;
            while((codeLine = codeReader.readLine()) != null) {
                if(curLine == lineNumber) {
                    break;
                }
                curLine++;
            }
        } catch(IOException e) {
            log.error("Error parsing source file: {}.", filePath);
        }
        return codeLine.replace("\r","").replace("\n","");
    }

    @Override
    public Integer getProjectId(String ownerId, String name) {
        log.info("Retrieving OD Project List");
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        ResponseEntity<OdProjectList> response = restTemplate.exchange(
                cxProperties.getUrl().concat(GET_PROJECTS),
                HttpMethod.GET,
                httpEntity,
                OdProjectList.class,
                ownerId);
        OdProjectList appList = response.getBody();
        for(OdProjectListDataItem item : appList.getData().getItems()) {
            if(item.getName().equals(name)) {
                CxScanParams csp = getScanProbeByTeam(ownerId);
                csp.setProjectId(item.getId());
                return item.getId();
            }
        }
        return -1;
    }

    public void waitForScanCompletion(Integer scanId) throws CheckmarxException {
        CxScanParams params = scanIdMap.get(scanId.toString());
        Integer projectId = params.getProjectId();
        Integer status = getScanStatus(projectId, scanId);
        long timer = 0;
        try {
            while(!status.equals(CxService.SCAN_STATUS_FINISHED) &&
                    !status.equals(CxService.SCAN_STATUS_CANCELED) &&
                    !status.equals(CxService.SCAN_STATUS_FAILED)) {
                Thread.sleep(cxProperties.getScanPolling());
                status = getScanStatus(projectId, scanId);
                timer += cxProperties.getScanPolling();
                if(timer >= (cxProperties.getScanTimeout() * 60000)) {
                    log.error("Scan timeout exceeded.  {} minutes", cxProperties.getScanTimeout());
                    throw new CheckmarxException("Timeout exceeded during scan");
                }
            }
        } catch(InterruptedException e) {
            log.error("Thread sleep error waiting for scan status!");
        }
        if (status.equals(CxService.SCAN_STATUS_FAILED) || status.equals(CxService.SCAN_STATUS_CANCELED)) {
            throw new CheckmarxException("Scan was cancelled or failed");
        }
    }

    public Integer getScanStatus(Integer projectId, Integer scanId) {
        log.debug("Retrieving OD Scan List");
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        ResponseEntity<OdScanList> response = restTemplate.exchange(
                cxProperties.getUrl().concat(GET_SCAN_STATUS),
                HttpMethod.GET,
                httpEntity,
                OdScanList.class,
                projectId);
        OdScanList appList = response.getBody();
        for(OdScanListDataItem item : appList.getData().getItems()) {
            if(item.getId().equals(scanId) && item.getStatus().equals("Done")) {
                return SCAN_STATUS_FINISHED;
            } else if(item.getId().equals(scanId) && item.getStatus().equals("Project sources scan failed")) {
                return SCAN_STATUS_FAILED;
            }
        }
        return -1;
    }

    /**
     * CxOD doesn't have projects in the same sense as normal SAST, this fakes
     * it a little bit.
     *
     * @param projectId - the ID of the project setup
     * @return the "simulated" project information
     */
    @Override
    public CxProject getProject(Integer projectId) {
        List<CxProject.CustomField> customFields = new ArrayList<>();
        CxProject cp = CxProject.builder()
                .id(projectId)
                .isPublic(true)
                .name("CXOD Temporary Project")
                .teamId(null)
                .links(null)
                .customFields(customFields)
                .build();
        return cp;
    }

    /**
     * If this is used for CxFlow /scanresults API calls. The ScanID will only contain the
     * scan record if CxOD hasn't been restarted since the scan was run. This ensures the
     * scan record is available in memory so that CxService can correctly look up the values.
     *
     * @param scanID
     * @param projectID
     */
    private void setupScanIdMap(Integer scanID, Integer projectID) {
        CxScanParams csp = getScanProbeByProject(projectID.toString());
        if(csp != null) {
            scanIdMap.put(scanID.toString(), csp);
        }
    }

    private OdScanList getProjectScanList(Integer projectId) {
        log.debug("Retrieving OD Scan List");
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        ResponseEntity<OdScanList> response = restTemplate.exchange(
                cxProperties.getUrl().concat(GET_SCAN_STATUS),
                HttpMethod.GET,
                httpEntity,
                OdScanList.class,
                projectId);
        return response.getBody();
    }

    @Override
    public Integer getLastScanId(Integer projectId) {
        OdScanList appList = getProjectScanList(projectId);
        for(OdScanListDataItem item : appList.getData().getItems()) {
            if(item.getStatus().equals("Done")) {
                this.setupScanIdMap(item.getId(), projectId);
                return item.getId();
            }
        }
        return -1;
    }

    /**
     * Examins the current scan scanProbeMap and returns the record matching the teamID
     * 'if' it exsits.
     *
     * @param teamID
     * @return the CxScanParams record
     */
    private CxScanParams getScanProbeByTeam(String teamID) {
        // First check it if it exists
        for(CxScanParams csp: scanProbeMap) {
            if(csp.getTeamId().equals(teamID)) {
                return csp;
            }
        }
        // If it doesn't exist then create it
        CxScanParams csp = new CxScanParams();
        csp.setTeamId(teamID);
        scanProbeMap.add(csp);
        return csp;
    }

    /**
     * Examins the current scan scanProbeMap and returns the record matching the teamID
     * 'if' it exsits.
     *
     * @param projectID
     * @return the CxScanParams record
     */
    private CxScanParams getScanProbeByProject(String projectID) {
        for(CxScanParams csp: scanProbeMap) {
            if(csp.getProjectId().toString().equals(projectID)) {
                return csp;
            }
        }
        return null;
    }

    //
    /// I think things below here should be removed the public interface. They are specific
    /// Cx SAST.
    //
    @Override
    public String getTeamId(String parentTeamId, String teamName) throws CheckmarxException {
        return UNKNOWN;
    }

    @Override
    public Integer getScanStatus(Integer scanId) {
        return 0;
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
    public Integer createScanReport(Integer scanId) {
        return null;
    }

    @Override
    public Integer getReportStatus(Integer reportId) throws CheckmarxException {
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
    public CxScanSettings getScanSettingsDto(int projectId) {
        return null;
    }

    @Override
    public String getPresetName(Integer presetId) {
        return null;
    }

    @Override
    public Integer getProjectPresetId(Integer projectId) {
        return -1;
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
    public void deleteTeamWS(String teamId) throws CheckmarxException {

    }

    @Override
    public Integer getScanConfiguration(String configuration) throws CheckmarxException {
        return null;
    }

    @Override
    public String getScanConfigurationName(int configurationId) {
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

    public Integer getScanIdOfExistingScanIfExists(Integer projectId) {
        return 0;
    }

    @Override
    public void deleteScan(Integer scanId) throws CheckmarxException {

    }

    @Override
    public void cancelScan(Integer scanId) throws CheckmarxException {

    }

    @Override
    public ScanResults getLatestScanResults(String teamName, String projectName, List<Filter> filters) throws CheckmarxException {
        return null;
    }

    @Override
    public Integer getLdapServerId(String serverName) throws CheckmarxException {
        return null;
    }

    @Autowired
    public void setCxRepoFileService(CxRepoFileService cxRepoFileService) {
        this.cxRepoFileService = cxRepoFileService;
    }
}
