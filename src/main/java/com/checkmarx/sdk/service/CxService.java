package com.checkmarx.sdk.service;

import com.checkmarx.sdk.utils.ZipUtils;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;
import java.net.URI;
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
    private static final String GET_PROJECTS = "/projects/projects?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22applicationId%22%2C%22value%22%3A%22{app_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A0%2C%22pageSize%22%3A50%7D%2C%22sorting%22%3A%5B%5D%7D";
    private static final String GET_SCAN_STATUS = "/scans/scans?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A0%2C%22pageSize%22%3A50%7D%2C%22sorting%22%3A%5B%5D%7D";

    private static Map<String, Integer> scanIdMap = new HashMap();

    private final CxProperties cxProperties;
    private final CxAuthClient authClient;
    private final RestTemplate restTemplate;
    private Map<String, Object> codeCache = new HashMap<String, Object>();

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
        File test = new File(prepareRepoFile(params.getGitUrl(), params.getBranch()));
        //String s3FilePath = postS3File(bucketURL, "testProj.zip", test, s3Fields);
        String s3FilePath = postS3File(bucketURL, "archive.zip", test, s3Fields);
        prepareRepoFile(params.getGitUrl(), params.getBranch());
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
        scanIdMap.put(scanId, projectID);
        return Integer.parseInt(scanId);
    }

    private String prepareRepoFile(String gitURL, String branch) throws CheckmarxException {
        String srcPath;
        File pathFile = null;
        srcPath = cxProperties.getGitClonePath().concat("/").concat(UUID.randomUUID().toString());
        pathFile = new File(srcPath);
        try {
            log.info("Cloning code locally to {}", pathFile);
            Git.cloneRepository()
                    .setURI(gitURL)
                    .setBranch(branch)
                    .setBranchesToClone(Collections.singleton(branch))
                    .setDirectory(pathFile)
                    .call();
            String cxZipFile = cxProperties.getGitClonePath().concat("/").concat("cx.".concat(UUID.randomUUID().toString()).concat(".zip"));
            //ZipUtils.zipFile(srcPath, cxZipFile, flowProperties.getZipExclude());
            // TODO: Jeffa, enable the eclude option.
            ZipUtils.zipFile(srcPath, cxZipFile, null);
            return cxZipFile;
        } catch(GitAPIException | IOException e) {
            throw new CheckmarxException("Unable to clone Git Url.");
        }
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
                    return id.toString();
                } else {
                    return searchTreeChildren(buTokens, i, children);
                }
            }
        }
        return UNKNOWN;
    }

    private String searchTreeChildren(String []buTokens, int i, ArrayList<Object> children) {
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
                    return id.toString();
                } else {
                    return searchTreeChildren(buTokens, i, nodeChildren);
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
        //return createCxODBusinessUnit(parentID, teamName);
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
        Integer projectId = scanIdMap.get(scanId.toString());
        getScanQueries(scanResults, projectId, scanId);
        return scanResults.build();
    }

    private void getScanQueries(ScanResults.ScanResultsBuilder scanResults, Integer projectId, Integer scanId) {
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
            summarizeSeverity(scanSummary, item);
            ScanResults.XIssue.XIssueBuilder xib = ScanResults.XIssue.builder();
            String testURL = cxProperties.getUrl().concat("/scan/business-unit/76/application/10001/project/10006/scans/10088");
            // TODO: jeffa, the hard coded address needs removed
            // We also need the current BU and application ID somehow
            testURL = "https://cloud.checkmarx.net/scan/business-unit/3/application/10/project/%s/scans/%s";
            String.format(testURL, projectId, scanId);
            xib.link(testURL);
            xib.language(item.getLanguage());
            for(OdScanQueryCategory vulnerability : item.getCategories()) {
                xib.severity(vulnerability.getSeverity());
                xib.vulnerability(vulnerability.getTitle());
                getScanResults(xib, projectId, scanId, vulnerability.getId());
                ScanResults.XIssue issue = xib.build();
                if(!xIssueList.contains(issue)) {
                    xIssueList.add(issue);
                }
            }
        }
        // TODO: Jeffa, I think I can remove the scan summary stuff -- its in additional details
        scanResults.scanSummary(scanSummary);
        scanResults.xIssues(xIssueList);
        Map<String, Object> additionalDetails = new HashMap();
        Map<String, Integer> scanDetails = new HashMap();
        scanDetails.put("High", scanSummary.getHighSeverity());
        scanDetails.put("Medium", scanSummary.getMediumSeverity());
        scanDetails.put("Low", scanSummary.getLowSeverity());
        additionalDetails.put("flow-summary", scanDetails);
        scanResults.additionalDetails(additionalDetails);
    }

    private void summarizeSeverity(CxScanSummary scanSummary, OdScanQueryItem vulnerability) {
        if(scanSummary.getLowSeverity() == null) scanSummary.setLowSeverity(0);
        if(scanSummary.getMediumSeverity() == null) scanSummary.setMediumSeverity(0);
        if(scanSummary.getHighSeverity() == null) scanSummary.setHighSeverity(0);
        for(OdScanQuerySeverity severity : vulnerability.getSeverity()) {
            Integer cnt;
            if(severity.getSeverityId().equals("low")) {
                cnt = scanSummary.getLowSeverity() + severity.getAmount();
                scanSummary.setLowSeverity(cnt);
            }
            if(severity.getSeverityId().equals("medium")) {
                cnt = scanSummary.getMediumSeverity() + severity.getAmount();
                scanSummary.setMediumSeverity(cnt);
            }
            if(severity.getSeverityId().equals("high")) {
                cnt = scanSummary.getHighSeverity() + severity.getAmount();
                scanSummary.setHighSeverity(cnt);
            }
        }
    }

    private void getScanResults(ScanResults.XIssue.XIssueBuilder xib, Integer projectId, Integer scanId, Integer queryId) {
        HttpEntity httpEntity = new HttpEntity<>(null, authClient.createAuthHeaders());
        ResponseEntity<OdScanResults> response = restTemplate.exchange(
                cxProperties.getUrl().concat(SCAN_RESULTS_ENCODED),
                HttpMethod.GET,
                httpEntity,
                OdScanResults.class,
                projectId,
                scanId,
                queryId
        );
        OdScanResults scanResults = response.getBody();
        for(OdScanResultItem item : scanResults.getData().getItems()) {
            xib.similarityId(item.getSimilarityId());
            xib.file(item.getSourceFile());
            //
            /// Read the call stack for the issue.
            //
            getScanResultNodes(xib, projectId, scanId, item.getId());
        }
    }

    private void getScanResultNodes(ScanResults.XIssue.XIssueBuilder xib, Integer projectId, Integer scanId, Integer resultId) {
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
        Map<Integer, ScanResults.IssueDetails> issueDetailsList = new HashMap<>();
        for(int i = 0; i < scanNodes.getData().getItems().size(); i++) {
            OdScanNodeItem item = scanNodes.getData().getItems().get(i);
            String snippet = extractCodeSnippet(projectId, scanId, item.getLine(), item.getFile().getId());
            ScanResults.IssueDetails issueDetails = new ScanResults.IssueDetails()
                    .codeSnippet(snippet)
                    .comment("")
                    .falsePositive(false);
            issueDetailsList.put(i, issueDetails);
        }
        xib.details(issueDetailsList);
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
                return item.getId();
            }
        }
        return -1;
    }

    public void waitForScanCompletion(Integer scanId) throws CheckmarxException {
        Integer projectId = scanIdMap.get(scanId.toString());
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
        log.info("Retrieving OD Scan List");
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        ResponseEntity<OdScanList> response = restTemplate.exchange(
                cxProperties.getUrl().concat(GET_SCAN_STATUS),
                HttpMethod.GET,
                httpEntity,
                OdScanList.class,
                projectId);
        OdScanList appList = response.getBody();
        for(OdScanListDataItem item : appList.getData().getItems()) {
            if(item.getId() == scanId && item.getStatus().equals("Done")) {
                return SCAN_STATUS_FINISHED;
            } else if(item.getId() == scanId && item.getStatus().equals("Project sources scan failed")) {
                return SCAN_STATUS_FAILED;
            }
        }
        return -1;
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
