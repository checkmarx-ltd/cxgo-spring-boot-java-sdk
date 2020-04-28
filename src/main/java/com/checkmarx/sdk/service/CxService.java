package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.*;
import com.checkmarx.sdk.dto.cx.xml.*;
import com.checkmarx.sdk.exception.CheckmarxLegacyException;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.config.Constants;
import com.checkmarx.sdk.exception.InvalidCredentialsException;
import com.checkmarx.sdk.utils.ScanUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Class used to orchestrate submitting scans and retrieving results
 */
@Service
public class CxService implements CxClient{

    private static final String UNKNOWN = "-1";
    private static final Integer UNKNOWN_INT = -1;
    private static final Integer SCAN_STATUS_NEW = 1;
    private static final Integer SCAN_STATUS_PRESCAN = 2;
    private static final Integer SCAN_STATUS_QUEUED = 3;
    private static final Integer SCAN_STATUS_SCANNING = 4;
    private static final Integer SCAN_STATUS_POST_SCAN = 6;
    private static final Integer SCAN_STATUS_FINISHED = 7;
    private static final Integer SCAN_STATUS_CANCELED = 8;
    private static final Integer SCAN_STATUS_FAILED = 9;
    private static final Integer SCAN_STATUS_SOURCE_PULLING = 10;
    private static final Integer SCAN_STATUS_NONE = 1001;
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
    private static final String TEAMS = "/auth/teams";
    private static final String TEAM = "/auth/teams/{id}";
    private static final String TEAM_LDAP_MAPPINGS_UPDATE = "/auth/LDAPServers/{id}/TeamMappings";
    private static final String TEAM_LDAP_MAPPINGS = "/auth/LDAPTeamMappings?ldapServerId={id}";
    private static final String TEAM_LDAP_MAPPINGS_DELETE = "/auth/LDAPTeamMappings/{id}";
    private static final String ROLE = "/auth/Roles";
    private static final String ROLE_LDAP_MAPPING = "/auth/LDAPServers/{id}/RoleMappings";
    private static final String ROLE_LDAP_MAPPINGS = "/auth/LDAPRoleMappings?ldapServerId={id}";
    private static final String ROLE_LDAP_MAPPINGS_DELETE = "/auth/LDAPRoleMappings/{id}";
    private static final String LDAP_SERVER = "/auth/LDAPServers";
    private static final String PROJECTS = "/projects";
    private static final String PROJECT = "/projects/{id}";
    private static final String PROJECT_SOURCE = "/projects/{id}/sourceCode/remoteSettings/git";
    private static final String PROJECT_SOURCE_FILE = "/projects/{id}/sourceCode/attachments";
    private static final String PROJECT_EXCLUDE = "/projects/{id}/sourceCode/excludeSettings";
    private static final String PRESETS = "/sast/presets";
    private static final String SCAN_CONFIGURATIONS = "/sast/engineConfigurations";
    private static final String SCAN_SETTINGS = "/sast/scanSettings";
    private static final String SCAN = "/sast/scans";
    private static final String SCAN_SUMMARY = "/sast/scans/{id}/resultsStatistics";
    private static final String PROJECT_SCANS = "/sast/scans?projectId={pid}";
    private static final String SCAN_STATUS = "/sast/scans/{id}";
    private static final String REPORT = "/reports/sastScan";
    private static final String REPORT_DOWNLOAD = "/reports/sastScan/{id}";
    private static final String REPORT_STATUS = "/reports/sastScan/{id}/status";
    private static final String OSA_VULN = "Vulnerable_Library";
    private final CxProperties cxProperties;
    private final CxLegacyService cxLegacyService;
    private final CxAuthClient authClient;
    private final RestTemplate restTemplate;

    public CxService(CxAuthClient authClient, CxProperties cxProperties, CxLegacyService cxLegacyService, @Qualifier("cxRestTemplate") RestTemplate restTemplate) {
        this.authClient = authClient;
        this.cxProperties = cxProperties;
        this.cxLegacyService = cxLegacyService;
        this.restTemplate = restTemplate;
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
    public String getTeamId(String teamPath) throws CheckmarxException {
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
    public String createTeam(String parentTeamId, String teamName) throws CheckmarxException {
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
    public Integer createScan(CxScanParams params, String comment) throws CheckmarxException {
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
