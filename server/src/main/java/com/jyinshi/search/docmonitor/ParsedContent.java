package com.jyinshi.search.docmonitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParsedContent {
    private String title;
    private int textLength;
    private int linksCount;
    private List<ParsedLink> allLinks = new ArrayList<>();
    private List<DramaEntry> dramaEntries = new ArrayList<>();
}
