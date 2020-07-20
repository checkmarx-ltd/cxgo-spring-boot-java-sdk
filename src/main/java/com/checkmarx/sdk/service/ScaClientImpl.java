package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.ScaProperties;
import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.sca.SCAParams;
import com.checkmarx.sdk.dto.sca.SCAResults;
import com.checkmarx.sdk.exception.SCARuntimeException;
import com.cx.restclient.CxShragaClient;
import com.cx.restclient.configuration.CxScanConfig;
import com.cx.restclient.dto.DependencyScanResults;
import com.cx.restclient.dto.DependencyScannerType;
import com.cx.restclient.sca.dto.RemoteRepositoryInfo;
import com.cx.restclient.sca.dto.SCAConfig;
import com.cx.restclient.sca.dto.SourceLocationType;
import com.cx.restclient.sca.dto.report.SCASummaryResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.EnumSet;

@Slf4j
@RequiredArgsConstructor
@Service
public class ScaClientImpl implements ScaClient {
    private static final String ERROR_PREFIX = "SCA scan cannot be initiated.";
    private static final int SCA_SCAN_INTERVAL_IN_SECONDS = 5;

    private final ScaProperties scaProperties;

    @Override
    public SCAResults scanRemoteRepo(SCAParams scaParams) throws IOException {
        validate(scaParams);

        CxScanConfig scanConfig = getScanConfig(scaParams);
        scanConfig.setOsaProgressInterval(SCA_SCAN_INTERVAL_IN_SECONDS);
        DependencyScanResults scanResults = executeScan(scanConfig);

        SCAResults scaResults = toScaResults(scanResults.getScaResults());
        applyScaResultsFilters(scaResults);

        return scaResults;
    }

    @Override
    public SCAResults scanLocalSource(SCAParams scaParams) throws IOException {
        validate(scaParams);

        CxScanConfig scanConfig = getScanConfig(scaParams);
        scanConfig.setZipFile(new File(scaParams.getZipPath()));
        scanConfig.setOsaProgressInterval(SCA_SCAN_INTERVAL_IN_SECONDS);
        /*
        TODO
        ...
        LOGIC for Resolver functionality (package manager)
        ...
         */
        DependencyScanResults scanResults = executeScan(scanConfig);

        SCAResults scaResults = toScaResults(scanResults.getScaResults());
        applyScaResultsFilters(scaResults);

        return scaResults;
    }

    private void applyScaResultsFilters(SCAResults scaResults) {
        if (scaProperties.getFilterSeverity() != null && !Objects.requireNonNull(scaProperties.getFilterSeverity()).isEmpty()) {
            filterResultsBySeverity(scaResults, scaProperties.getFilterSeverity());
        }

        Double filterScore = scaProperties.getFilterScore();
        if (filterScore != null && filterScore >= 0.0) {
            filterResultsByScore(scaResults, filterScore);
        } else  {
            log.info("Cx-SCA filter score is not defined", filterScore); ;
        }
    }

    private void filterResultsBySeverity(SCAResults scaResults, List<String> filerSeverity) {
        List<String> validateFilterSeverity = validateFilterSeverity(filerSeverity);
        log.info("Applying Cx-SCA results filter severities: [{}]", validateFilterSeverity.toString());
        scaResults.getFindings().removeIf(finding -> (
                !StringUtils.containsIgnoreCase(validateFilterSeverity.toString(), finding.getSeverity().name())
        ));
    }

    private void filterResultsByScore(SCAResults scaResults, double score) {
        log.info("Applying Cx-SCA results filter score: [{}]", score);
        scaResults.getFindings().removeIf(finding -> (
                finding.getScore() < score
        ));
    }

    private List<String> validateFilterSeverity(List<String> filerSeverity) {
        Iterator<String> iterator = filerSeverity.iterator();
        while (iterator.hasNext()) {
            String nextFilter = iterator.next();
            if (!StringUtils.containsIgnoreCase(EnumSet.range(Filter.Severity.HIGH, Filter.Severity.LOW).toString(), nextFilter)) {
                log.warn("Severity: [{}] is not a supported filter", nextFilter);
                iterator.remove();
            }
        }
        return filerSeverity;
    }

    /**
     * Convert Common Client representation of SCA results into an object from this SDK.
     */
    private SCAResults toScaResults(com.cx.restclient.sca.dto.SCAResults scaResultsFromCommonClient) {
        validateNotNull(scaResultsFromCommonClient);

        SCASummaryResults summary = scaResultsFromCommonClient.getSummary();
        Map<Filter.Severity, Integer> findingCountsPerSeverity = getFindingCountMap(summary);

        ModelMapper mapper = new ModelMapper();
        SCAResults result = mapper.map(scaResultsFromCommonClient, SCAResults.class);
        result.getSummary().setFindingCounts(findingCountsPerSeverity);
        return result;
    }

    private Map<Filter.Severity, Integer> getFindingCountMap(SCASummaryResults summary) {
        EnumMap<Filter.Severity, Integer> result = new EnumMap<>(Filter.Severity.class);
        result.put(Filter.Severity.HIGH, summary.getHighVulnerabilityCount());
        result.put(Filter.Severity.MEDIUM, summary.getMediumVulnerabilityCount());
        result.put(Filter.Severity.LOW, summary.getLowVulnerabilityCount());
        return result;
    }

    /**
     * Convert scaParams to an object that is used by underlying logic in Common Client.
     */
    private CxScanConfig getScanConfig(SCAParams scaParams) {
        CxScanConfig cxScanConfig = new CxScanConfig();
        cxScanConfig.setDependencyScannerType(DependencyScannerType.SCA);
        cxScanConfig.setSastEnabled(false);
        cxScanConfig.setProjectName(scaParams.getProjectName());
        cxScanConfig.setScaConfig(getSCAConfig(scaParams));

        return cxScanConfig;
    }

    private SCAConfig getSCAConfig(SCAParams scaParams) {
        SCAConfig scaConfig = new SCAConfig();
        scaConfig.setWebAppUrl(scaProperties.getAppUrl());
        scaConfig.setApiUrl(scaProperties.getApiUrl());
        scaConfig.setAccessControlUrl(scaProperties.getAccessControlUrl());
        scaConfig.setTenant(scaProperties.getTenant());
        scaConfig.setUsername(scaProperties.getUsername());
        scaConfig.setPassword(scaProperties.getPassword());
        if(scaParams.getZipPath() != null){
            scaConfig.setSourceLocationType(SourceLocationType.LOCAL_DIRECTORY);
        }
        else{
            scaConfig.setSourceLocationType(SourceLocationType.REMOTE_REPOSITORY);
            RemoteRepositoryInfo remoteRepoInfo = new RemoteRepositoryInfo();
            remoteRepoInfo.setUrl(scaParams.getRemoteRepoUrl());
            scaConfig.setRemoteRepositoryInfo(remoteRepoInfo);
        }

        return scaConfig;
    }

    private DependencyScanResults executeScan(CxScanConfig cxScanConfig) throws IOException {
        CxShragaClient client = new CxShragaClient(cxScanConfig, log);
        client.init();
        client.createDependencyScan();

        return client.waitForDependencyScanResults();
    }

    private void validate(SCAParams scaParams) {
        validateNotNull(scaParams);
        validateNotEmpty(scaProperties.getAppUrl(), "SCA application URL");
        validateNotEmpty(scaProperties.getApiUrl(), "SCA API URL");
        validateNotEmpty(scaProperties.getAccessControlUrl(), "SCA Access Control URL");
        validateNotEmpty(scaParams.getProjectName(), "Project name");
        validateNotEmpty(scaProperties.getTenant(), "SCA tenant");
        validateNotEmpty(scaProperties.getUsername(), "Username");
        validateNotEmpty(scaProperties.getPassword(), "Password");
    }

    private void validateNotNull(SCAParams scaParams) {
        if (scaParams == null) {
            throw new SCARuntimeException(String.format("%s SCA parameters weren't provided.", ERROR_PREFIX));
        }

        if (scaParams.getRemoteRepoUrl() == null && scaParams.getZipPath() == null) {
            throw new SCARuntimeException(String.format("%s Repository URL or Zip path wasn't provided.", ERROR_PREFIX));
        }

        if(!(new File(scaParams.getZipPath()).exists())){
            throw new SCARuntimeException(String.format("%s file (%s) does not exist.", ERROR_PREFIX, scaParams.getZipPath()));
        }
    }

    private void validateNotNull(com.cx.restclient.sca.dto.SCAResults scaResults) {
        if (scaResults == null) {
            throw new SCARuntimeException("SCA results are missing.");
        }

        SCASummaryResults summary = scaResults.getSummary();
        if (summary == null) {
            throw new SCARuntimeException("SCA results don't contain a summary.");
        }
    }

    private void validateNotEmpty(String parameter, String parameterDescr) {
        if (StringUtils.isEmpty(parameter)) {
            String message = String.format("%s %s wasn't provided", ERROR_PREFIX, parameterDescr);
            throw new SCARuntimeException(message);
        }
    }
}