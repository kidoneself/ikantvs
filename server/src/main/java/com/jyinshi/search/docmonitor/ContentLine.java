package com.jyinshi.search.docmonitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Fetcher 抽好的一行文本（段落/块）+ 行内网盘 URL，交给 {@link DramaAggregator}。 */
@Data
public class ContentLine {
    private String text = "";
    private final List<String> urls = new ArrayList<>();
}
