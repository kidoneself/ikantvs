package com.jyinshi.search.docmonitor;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.search.entity.DocMonitorTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocFetcherRegistry {

    private final Map<String, DocFetcher> bySource = new LinkedHashMap<>();

    public DocFetcherRegistry(List<DocFetcher> fetchers) {
        if (fetchers != null) {
            for (DocFetcher f : fetchers) {
                bySource.put(f.source().toLowerCase(), f);
            }
        }
    }

    public DocFetcher require(String source) {
        String key = StringUtils.hasText(source) ? source.trim().toLowerCase() : "flowus";
        DocFetcher f = bySource.get(key);
        if (f == null) {
            throw new BizException("不支持的文档来源: " + key + "（已注册: " + bySource.keySet() + "）");
        }
        return f;
    }

    public DocFetcher resolve(DocMonitorTask task) {
        return require(task == null ? null : task.getSource());
    }

    public Collection<String> sources() {
        return bySource.keySet();
    }
}
