package com.checkmarx.sdk.service;

import com.checkmarx.sdk.dto.Filter;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.exception.CheckmarxException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CxOsaService implements CxOsaClient{
    @Override
    public ScanResults createScanAndReport(Integer projectId, String sourceDir, ScanResults results, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public String createScan(Integer projectId, String sourceDir) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults waitForOsaScan(String scanId, Integer projectId, ScanResults results, List<Filter> filter) throws CheckmarxException {
        return null;
    }

    @Override
    public ScanResults getLatestOsaResults(Integer projectId, ScanResults results, List<Filter> filter) throws CheckmarxException {
        return null;
    }
}
