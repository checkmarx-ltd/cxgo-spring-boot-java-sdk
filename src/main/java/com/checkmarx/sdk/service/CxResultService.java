package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.od.OdScanResultItem;
import com.checkmarx.sdk.dto.od.OdScanResults;
import com.checkmarx.sdk.dto.od.ScanResults;
import com.checkmarx.sdk.exception.CheckmarxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Class used to retrie results
 */
@Service
@Slf4j
public class CxResultService {
    private static final String SCAN_RESULTS_ENCODED = "/results/results?criteria=%7B%22criteria%22%3A%5B%7B%22key%22%3A%22projectId%22%2C%22value%22%3A%22{project_id}%22%7D%2C%7B%22key%22%3A%22scanId%22%2C%22value%22%3A%22{scan_id}%22%7D%5D%2C%22pagination%22%3A%7B%22currentPage%22%3A{current_page%2C%22pageSize%22%3A{page_size}%7D%7D";
    private static final String SCAN_RESULTS = "/v1/scans/{scan_id}/results";
    private final CxAuthClient authClient;
    private final RestTemplate restTemplate;
    private final CxProperties cxProperties;

    public CxResultService(CxAuthClient authClient, @Qualifier("cxRestTemplate") RestTemplate restTemplate, CxProperties cxProperties) {
        this.authClient = authClient;
        this.restTemplate = restTemplate;
        this.cxProperties = cxProperties;
    }

    @Async
    CompletableFuture<ScanResults> getScanResults(Integer scanId) throws CheckmarxException {
        HttpEntity<?> httpEntity = new HttpEntity<>(authClient.createAuthHeaders());

        try {
            log.info("Retrieving Scan Results for Scan Id {} ", scanId);
            ResponseEntity<ScanResults> response = restTemplate.exchange(
                    //ResponseEntity<String> response = restTemplate.exchange(
                    cxProperties.getUrl().concat(SCAN_RESULTS),
                    HttpMethod.GET,
                    httpEntity,
                    com.checkmarx.sdk.dto.od.ScanResults.class,
                    //String.class,
                    scanId);
            //return null;
            return CompletableFuture.completedFuture(response.getBody());
        } catch(HttpStatusCodeException e) {
            log.error("Error occurred while retrieving the scan results for id {}.", scanId);
            log.error(ExceptionUtils.getStackTrace(e));
            throw new CheckmarxException("Error occurred while retrieving the scan status for id ".concat(Integer.toString(scanId)));
        }
    }


    @Async
    CompletableFuture<Map<String, OdScanResultItem>> getScanResultsPage(Integer projectId, Integer scanId) {
        HttpEntity<?> httpEntity = new HttpEntity<>(authClient.createAuthHeaders());
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
        //create a map lookup based on the id
        return CompletableFuture.completedFuture(
                appList.getData()
                        .getItems()
                        .stream()
                        .collect(Collectors.toMap(
                                i -> i.getId().toString(), i -> i, (a, b) -> b)
                        )
        );
    }

}
