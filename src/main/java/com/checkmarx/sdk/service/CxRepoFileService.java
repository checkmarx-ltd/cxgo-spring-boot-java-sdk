package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.utils.ZipUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Service
public class CxRepoFileService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CxRepoFileService.class);
    private final CxProperties cxProperties;

    public CxRepoFileService(CxProperties cxProperties) {
        this.cxProperties = cxProperties;
    }

    public String prepareRepoFile(String gitURL, String branch) throws CheckmarxException {
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
}
