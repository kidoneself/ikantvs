package com.jyinshi.search.docmonitor;

import lombok.Data;

@Data
public class ParsedLink {
    private String url;
    private String text;
    private String type;
}
