package com.jyinshi.search.docmonitor;

import lombok.Data;

/** 文档内聚合出的一条剧目 + 网盘链。 */
@Data
public class DramaEntry {
    private String name;
    private String fullTitle;
    private String quarkUrl;
    private String baiduUrl;
    private String xunleiUrl;
    /** 来源：flowus / kdocs / … */
    private String source;
}
