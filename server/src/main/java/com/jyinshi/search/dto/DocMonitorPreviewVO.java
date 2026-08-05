package com.jyinshi.search.dto;

import com.jyinshi.search.docmonitor.DramaEntry;
import com.jyinshi.search.docmonitor.ParseRules;
import lombok.Data;

import java.util.List;

@Data
public class DocMonitorPreviewVO {
    private String source;
    private String title;
    private int linksCount;
    private int textLength;
    private int dramaCount;
    private String fingerprint;
    private ParseRules appliedRules;
    private List<DramaEntry> dramas;
    private List<SampleLink> sampleLinks;

    @Data
    public static class SampleLink {
        private String url;
        private String type;
        private String text;
    }
}
