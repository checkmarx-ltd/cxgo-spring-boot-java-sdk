package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.*;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;
import com.checkmarx.sdk.dto.od.*;
import com.checkmarx.sdk.exception.CheckmarxException;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxService implements CxClient{
    private static final String UNKNOWN = "-1";
    private static final Integer UNKNOWN_INT = -1;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxService.class);

    //
    /// Rest API endpoints
    //
    private static final String CREATE_SCAN = "/v1/scans";
    private static final String SCAN_STATUS = "/v1/scans/{scan_id}/status";
    private static final String SCAN_RESULTS = "/v1/scans/{scan_id}/results";
    private static final String SCAN = "/v1/scans/{scan_id}";
    private static final String SCANS = "/v1/scans";
    private static final String SCAN_QUERIES = "/projects/projects/{project_id}/scans/{scan_id}/queries";
    private static final String SCAN_RESULTS_ENCODED = "/results/results?criteria=%7B%22filters%22%3A%5B%5D%2C%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%2C%7B%22key%22%3A%22scanId%22%2C%22value%22%3A%22{scan_id}%22%7D%2C%7B%22key%22%3A%22queryId%22%2C%22value%22%3A%22{query_id}%22%7D%5D%2C%22sorting%22%3A%5B%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A{cur_page}%2C%22pageSize%22%3A{page_size}%7D%7D";
    private static final String SCAN_RESULT_NODES_ENCODED = "/nodes/nodes?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%2C%7B%22key%22%3A%22scanId%22%2C%22value%22%3A%22{scan_id}%22%7D%2C%7B%22key%22%3A%22resultId%22%2C%22value%22%3A%22{result_id}%22%7D%5D%2C%22sorting%22%3A%5B%5D%7D";
    private static final String SCAN_FILE = "/projects/projects/{project_id}/scans/{scan_id}/files?filePath={file_path};";
    private static final String CREATE_APPLICATION = "/applications/applications";
    private static final String CREATE_PROJECT = "/projects/projects";
    private static final String GET_PROJECTS = "/projects/projects?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22applicationId%22%2C%22value%22%3A%22{app_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A{cur_page}%2C%22pageSize%22%3A{page_size}%7D%2C%22sorting%22%3A%5B%5D%7D";
    private static final String GET_SCAN_STATUS = "/scans/scans?criteria=%7B%22filters%22%3A%5B%5D%2C%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%5D%2C%22sorting%22%3A%5B%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A{cur_page}%2C%22pageSize%22%3A{page_size}%7D%7D";
    private static final String DEEP_LINK = "/scan/business-unit/%s/application/%s/project/%s/scans/%s";

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
    private final FilterValidator filterValidator;
    private CxRepoFileService cxRepoFileService;

    public CxService(CxAuthClient authClient,
                     CxProperties cxProperties,
                     @Qualifier("cxRestTemplate") RestTemplate restTemplate,
                     FilterValidator filterValidator) {
        this.authClient = authClient;
        this.cxProperties = cxProperties;
        this.restTemplate = restTemplate;
        this.filterValidator = filterValidator;
    }

    private String createApplication(String appName, String appDesc, String baBuId) {
        log.info("Creating new CxOD application {}.", appName);
        HttpEntity<String> httpEntity = new HttpEntity<>(
                getJSONCreateAppReq(appName, appDesc, baBuId),
                authClient.createAuthHeaders());
        ResponseEntity<OdApplicationCreate> createResp = restTemplate.exchange(
                cxProperties.getUrl().concat(CREATE_APPLICATION),
                HttpMethod.PUT,
                httpEntity,
                OdApplicationCreate.class);
        OdApplicationCreate appCreate = createResp.getBody();
        assert appCreate != null;
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
            createBody.put("licenseType", "standard");
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
        try {
            String appID = params.getTeamId();
            Integer projectID = getProjectId(appID, params.getProjectName());
            if (projectID.equals(UNKNOWN_INT)) {
                projectID = Integer.parseInt(createCxODProject(appID, params.getProjectName()));
            }
            params.setProjectId(projectID);
            /// Create the scan
            CreateScan scan = CreateScan.builder()
                    .projectId(params.getProjectId())
                    .engineTypes(cxProperties.getEngineTypes())
                    .build();
            log.info("Sending scan to CxOD for projectID {}.", params.getProjectId());

            HttpHeaders headers = authClient.createAuthHeaders();
            HttpEntity<CreateScan> httpEntity = new HttpEntity<>(scan, headers);
            ResponseEntity<CreateScanResponse> createResp = restTemplate.exchange(
                    cxProperties.getUrl().concat(CREATE_SCAN),
                    HttpMethod.POST,
                    httpEntity,
                    CreateScanResponse.class);
            CreateScanResponse scanCreate = createResp.getBody();

            assert scanCreate != null;

            Integer scanId = scanCreate.getScan().getId();
            log.info("CxOD started scan with scanId {}.", scanId);
            ///The repo to be scanned is uploaded to amazon bucket
            log.info("CxOD Uploading Scan file {}.", scanId);

            File archive;
            if (params.getSourceType() == CxScanParams.Type.FILE) {
                archive = new File(params.getFilePath());
            } else {
                archive = new File(cxRepoFileService.prepareRepoFile(params));
            }

            uploadScanFile(scanCreate.getStorage(), archive);
            FileSystemUtils.deleteRecursively(archive);
            return scanId;
        }catch (HttpClientErrorException | HttpServerErrorException e){
            log.error("Http Exception: {}", ExceptionUtils.getRootCauseMessage(e), e);
            throw new CheckmarxException("Http error occurred");
        }catch (NullPointerException e){
            log.error("Null Exception: {}", ExceptionUtils.getRootCauseMessage(e), e);
            throw new CheckmarxException("NullPointerException occurred");
        }
    }

    /**
     * Upload Source to pre-signed URL
     *
     * @param scanStorage Response Object from CxGo for S3 details
     * @param file File to upload
     * @throws CheckmarxException
     */
    private void uploadScanFile(Storage scanStorage, File file) throws CheckmarxException{
        try {
            Fields scanFields = scanStorage.getFields();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("key", scanFields.getKey());
            body.add("bucket", scanFields.getBucket());
            body.add("X-Amz-Algorithm", scanFields.getXAmzAlgorithm());
            body.add("X-Amz-Credential", scanFields.getXAmzCredential());
            body.add("X-Amz-Date", scanFields.getXAmzDate());
            body.add("X-Amz-Security-Token", scanFields.getXAmzSecurityToken());
            body.add("Policy", scanFields.getPolicy());
            body.add("X-Amz-Signature", scanFields.getXAmzSignature());

            FileSystemResource fsr = new FileSystemResource(file);
            body.add("file", fsr);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            restTemplate.exchange(
                    scanStorage.getUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
        } catch(HttpClientErrorException e) {
            log.error("CxOD error uploading file.", e);
            throw new CheckmarxException("Error Uploading Source to ".concat(scanStorage.getUrl()));
        }
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
            log.debug("Retrieving OD Navigation Tree");
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

    @Override
    public ScanResults getReportContentByScanId(Integer scanId, FilterConfiguration filter) throws CheckmarxException {
        ScanResults.ScanResultsBuilder scanResults = ScanResults.builder();
        Scan scan = getScanDetails(scanId);
        Integer projectId = scan.getProjectId();
        Integer buId = scan.getBusinessUnitId();
        Integer appId = scan.getApplicationId();
        getScanQueries(scanResults, projectId, scanId, buId, appId, filter);
        scanResults.projectId(projectId.toString());
        String deepLink = cxProperties.getPortalUrl().concat(DEEP_LINK);
        deepLink = String.format(deepLink, buId, appId, projectId, scanId);
        scanResults.link(deepLink);
        return scanResults.build();
    }

    private void getScanQueries(ScanResults.ScanResultsBuilder scanResults,
                                    Integer projectId,
                                    Integer scanId,
                                    Integer buId,
                                    Integer appId,
                                    FilterConfiguration filter) throws CheckmarxException {
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


    public com.checkmarx.sdk.dto.od.ScanResults getScanResults(Integer scanId) throws CheckmarxException {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.info("Retrieving Scan Results for Scan Id {} ", scanId);
            //ResponseEntity<com.checkmarx.sdk.dto.od.ScanResults> response = restTemplate.exchange(
            ResponseEntity<String> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN_RESULTS),
                    HttpMethod.GET,
                    httpEntity,
                    //com.checkmarx.sdk.dto.od.ScanResults.class,
                    String.class,
                    scanId);
            return null;
            //return response.getBody();
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving the scan results for id {}.", scanId);
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error occurred while retrieving the scan status for id ".concat(Integer.toString(scanId)));
        }
    }

    private void getScanResults(Integer buId,
                                    Integer appId,
                                    Integer projectId,
                                    Integer scanId,
                                    String language,
                                    OdScanQueryCategory vulnerability,
                                    CxScanSummary scanSummary,
                                    List<ScanResults.XIssue> xIssueList,
                                    FilterConfiguration filter) {

        OdScanResults scanResults = getScanResultsPage(projectId, scanId, vulnerability);
        for(OdScanResultItem item : scanResults.getData().getItems()) {
            if(filterValidator.passesFilter(vulnerability, item, filter)) {
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

    private OdScanResults getScanResultsPage(Integer projectId, Integer scanId, OdScanQueryCategory vulnerability) {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        OdScanResults appList = new OdScanResults();
        boolean morePages = true;
        int curPage = 0;
        int pageSize = 50;
        long totalCount = 0;
        long rcvItemCnt = 0;
        while(morePages) {
            // Fetch the current page
            ResponseEntity<OdScanResults> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN_RESULTS_ENCODED),
                    HttpMethod.GET,
                    httpEntity,
                    OdScanResults.class,
                    projectId,
                    scanId,
                    vulnerability.getId(),
                    curPage,
                    pageSize
            );
            // Are there more results
            OdScanResults curList = response.getBody();
            if(curPage == 0) totalCount = curList.getData().getTotalCount();
            rcvItemCnt += curList.getData().getItems().size();
            // There are more items, add them to the list
            if (appList.getData() == null) {
                appList.setData(curList.getData());
            } else {
                appList.getData().getItems().addAll(curList.getData().getItems());
            }
            if(rcvItemCnt < totalCount) {
                curPage++;
            } else {
                morePages = false;
            }
        }
        return appList;
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
        assert codeLine != null;
        return codeLine.replace("\r","").replace("\n","");
    }

    @Override
    public Integer getProjectId(String ownerId, String name) {
        log.info("Retrieving OD Project List");
        OdProjectList appList = getProjectPage(ownerId);
        for(OdProjectListDataItem item : appList.getData().getItems()) {
            if(item.getName().equals(name)) {
                CxScanParams csp = getScanProbeByTeam(ownerId);
                csp.setProjectId(item.getId());
                return item.getId();
            }
        }
        return UNKNOWN_INT;
    }

    private OdProjectList getProjectPage(String ownerId) {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        OdProjectList appList = new OdProjectList();
        boolean morePages = true;
        int curPage = 0;
        int pageSize = 50;
        long totalCount = 0;
        long rcvItemCnt = 0;
        while(morePages) {
            // Fetch the current page
            ResponseEntity<OdProjectList> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(GET_PROJECTS),
                    HttpMethod.GET,
                    httpEntity,
                    OdProjectList.class,
                    ownerId,
                    curPage,
                    pageSize);
            // Are there more results
            OdProjectList curList = response.getBody();
            if(curPage == 0) totalCount = curList.getData().getTotalCount();
            rcvItemCnt += curList.getData().getItems().size();
            // There are more items, add them to the list
            if (appList.getData() == null) {
                appList.setData(curList.getData());
            } else {
                appList.getData().getItems().addAll(curList.getData().getItems());
            }
            if(rcvItemCnt < totalCount) {
                curPage++;
            } else {
                morePages = false;
            }
        }
        return appList;
    }

    @Override
    public void waitForScanCompletion(Integer scanId) throws CheckmarxException {
        ScanStatus scanStatus = getScanStatusById(scanId);
        ScanStatus.Status status = scanStatus.getStatus();
        long timer = 0;
        try {
            while(!status.equals(ScanStatus.Status.COMPLETED) &&
                    !status.equals(ScanStatus.Status.FAILED)) {
                Thread.sleep(cxProperties.getScanPolling());
                scanStatus = getScanStatusById(scanId);
                status = scanStatus.getStatus();
                timer += cxProperties.getScanPolling();
                log.info("scanId: {}, status: {}, progress: {}", scanId, scanStatus.getStatus(), scanStatus.getProgress());
                if(timer >= (cxProperties.getScanTimeout() * 60000)) {
                    log.error("Scan timeout exceeded.  {} minutes", cxProperties.getScanTimeout());
                    throw new CheckmarxException("Timeout exceeded during scan");
                }
            }
        } catch(InterruptedException e) {
            log.error("Thread sleep error waiting for scan status!");
        }
        log.info("scanId: {}, status: {}, progress: {}", scanId, scanStatus.getStatus(), scanStatus.getProgress());
        if (status.equals(ScanStatus.Status.FAILED)) {
            throw new CheckmarxException("Scan was cancelled or failed");
        }
    }

    private OdScanList getScanStatusPage(Integer projectId) {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        OdScanList appList = new OdScanList();
        boolean morePages = true;
        int curPage = 0;
        int pageSize = 50;
        long totalCount = 0;
        long rcvItemCnt = 0;
        while(morePages) {
            // Fetch the current page
            ResponseEntity<OdScanList> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(GET_SCAN_STATUS),
                    HttpMethod.GET,
                    httpEntity,
                    OdScanList.class,
                    projectId,
                    curPage,
                    pageSize);
            // Are there more results
            OdScanList curList = response.getBody();
            if(curPage == 0) totalCount = curList.getData().getTotalCount();
            rcvItemCnt += curList.getData().getItems().size();
            // There are more items, add them to the list
            if (appList.getData() == null) {
                appList.setData(curList.getData());
            } else {
                appList.getData().getItems().addAll(curList.getData().getItems());
            }
            if(rcvItemCnt < totalCount) {
                curPage++;
            } else {
                morePages = false;
            }
        }
        return appList;
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
        CxProject.CxProjectBuilder builder = CxProject.builder();
        builder.id(projectId);
        builder.isPublic(true);
        builder.name("CXOD Temporary Project");
        builder.teamId(null);
        builder.links(null);
        builder.customFields(customFields);
        return builder.build();
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

    @Override
    public Integer getLastScanId(Integer projectId) {
        OdScanList appList = getScanStatusPage(projectId);
        for(OdScanListDataItem item : appList.getData().getItems()) {
            if(item.getStatus().equals("Done")) {
                this.setupScanIdMap(item.getId(), projectId);
                return item.getId();
            }
        }
        return UNKNOWN_INT;
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

    public Integer getScanStatus(Integer scanId) {
        return UNKNOWN_INT;
    }

    public ScanStatus getScanStatusById(Integer scanId) throws CheckmarxException {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.info("Retrieving OD Navigation Tree");
            ResponseEntity<ScanStatus> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN_STATUS),
                    HttpMethod.GET,
                    httpEntity,
                    ScanStatus.class,
                    scanId);
            return response.getBody();
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving the scan status for id {}.", scanId);
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error occurred while retrieving the scan status for id ".concat(Integer.toString(scanId)));
        }
    }

    public Scan getScanDetails(Integer scanId) throws CheckmarxException {
        List<Scan> scans = getScans();
        return scans.stream()
                .filter(s -> s.getId().equals(scanId))
                .findFirst()
                .orElseThrow(() -> new CheckmarxException("Scan was not found with id ".concat(scanId.toString())));
        /**
         * TODO Use this logic once the API works on CxGo
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.debug("Retrieving scan with id {}", scanId);
            ResponseEntity<Scan> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN),
                    HttpMethod.GET,
                    httpEntity,
                    Scan.class,
                    scanId);
            return response.getBody();
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving the scan with id {}", scanId);
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error occurred while retrieving the scan with id".concat(Integer.toString(scanId)));
        }
         */
    }

    public List<Scan> getScans() throws CheckmarxException {
        HttpEntity httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
        try {
            log.debug("Retrieving all scans");
            ResponseEntity<Scan[]> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCANS),
                    HttpMethod.GET,
                    httpEntity,
                    Scan[].class);
            return Arrays.asList(Objects.requireNonNull(response.getBody()));
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving scans");
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error occurred while retrieving scans");
        }
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
    public ScanResults getReportContent(Integer reportId, FilterConfiguration filter) throws CheckmarxException {
        return null;
    }

    @Override
    public Map<String, String> getCustomFields(Integer projectId) {
        return null;
    }

    @Override
    public ScanResults getReportContent(File file, FilterConfiguration filter) throws CheckmarxException {
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
        return UNKNOWN_INT;
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
        return UNKNOWN_INT;
    }

    @Override
    public void deleteScan(Integer scanId) throws CheckmarxException {

    }

    @Override
    public void cancelScan(Integer scanId) throws CheckmarxException {

    }

    @Override
    public ScanResults createScanAndReport(CxScanParams params, String comment, FilterConfiguration filter) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults getLatestScanResults(String teamName, String projectName, FilterConfiguration filter) throws CheckmarxException {
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
