package com.checkmarx.sdk.dto.sca;

import lombok.Getter;
import lombok.Setter;
import com.cx.restclient.sca.dto.report.Finding;
import com.cx.restclient.sca.dto.report.Package;
import java.util.List;

@Getter
@Setter
public class SCAResults {
    private String scanId;
    private Summary summary;
    private String webReportLink;
    private List<Finding> findings;
    private List<Package> packages;
}
