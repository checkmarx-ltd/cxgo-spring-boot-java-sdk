package com.checkmarx.sdk.service;

import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.utils.ZipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
            URI uri = new URI(gitURL);
            CredentialsProvider credentialsProvider = null;
            String token = uri.getUserInfo();
            if(token.startsWith("oauth2:")){
                log.debug("Using gitlab clone");
                token = token.replace("oauth2:","");
                gitURL = gitURL.replace(uri.getUserInfo(), "gitlab-ci-token:".concat(token));
                credentialsProvider = new UsernamePasswordCredentialsProvider("user", token);
            }
            else{
                credentialsProvider = new UsernamePasswordCredentialsProvider(token, "");
            }
            log.info("Cloning code locally to {}", pathFile);
            Git.cloneRepository()
                    .setURI(gitURL)
                    .setBranch(branch)
                    .setBranchesToClone(Collections.singleton(branch))
                    .setDirectory(pathFile)
                    .setCredentialsProvider(credentialsProvider)
                    .call()
                    .close();
            String cxZipFile = cxProperties.getGitClonePath().concat("/").concat("cx.".concat(UUID.randomUUID().toString()).concat(".zip"));
            // TODO: Jeffa, enable the exclude option.
            //ZipUtils.zipFile(srcPath, cxZipFile, flowProperties.getZipExclude());
            ZipUtils.zipFile(srcPath, cxZipFile, null);
            try {
                FileUtils.deleteDirectory(pathFile);
            } catch (IOException e){ //Do not thro
                log.warn("Error deleting file {} - {}", pathFile, ExceptionUtils.getRootCauseMessage(e));
            }
            return cxZipFile;
        } catch (GitAPIException | IOException | URISyntaxException e)  {
            log.error(ExceptionUtils.getRootCauseMessage(e));
            throw new CheckmarxException("Unable to clone Git Url.");
        }
    }


}
