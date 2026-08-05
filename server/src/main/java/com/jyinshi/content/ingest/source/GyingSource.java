package com.jyinshi.content.ingest.source;

import com.jyinshi.content.ingest.IngestPanFilter;
import com.jyinshi.content.ingest.IngestProperties;
import com.jyinshi.content.ingest.source.gying.GyingAccountPool;
import com.jyinshi.content.ingest.source.gying.GyingSearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 观影（Gying）来源插件：登录站点、搜剧名、拉详情页里的网盘链接，产出 {@link RawLink}。
 *
 * <p>只做「找链接」，归属识别/去重/入库/检测交给下游。需要账号才启用（见
 * {@code jyinshi.ingest.gying.accounts}）；账号池懒加载登录，不阻塞应用启动。
 */
@Slf4j
@Component
public class GyingSource implements LinkSource {

    private static final String SOURCE = "gying";

    private final IngestProperties props;
    private final IngestPanFilter panFilter;
    private final GyingAccountPool pool;

    public GyingSource(IngestProperties props, IngestPanFilter panFilter) {
        this.props = props;
        this.panFilter = panFilter;
        this.pool = new GyingAccountPool(props.getGying());
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && props.getGying().isEnabled() && pool.hasAccounts();
    }

    @Override
    public List<RawLink> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        Set<String> wantTypes = panFilter.resolve(props.getGying().getCloudTypes());
        if (wantTypes != null && wantTypes.isEmpty()) {
            return List.of();
        }
        try {
            List<GyingSearchClient.SearchResult> results = pool.search(keyword);
            List<RawLink> out = new ArrayList<>();
            for (GyingSearchClient.SearchResult result : results) {
                if (result.links == null) {
                    continue;
                }
                for (GyingSearchClient.PanLink link : result.links) {
                    String type = link.type == null ? "" : link.type.trim().toLowerCase();
                    if (!StringUtils.hasText(type) || !StringUtils.hasText(link.url)) {
                        continue;
                    }
                    if (wantTypes != null && !wantTypes.contains(type)) {
                        continue;
                    }
                    String note = StringUtils.hasText(link.workTitle) ? link.workTitle : result.title;
                    RawLink raw = RawLink.of(type, link.url.trim(), link.password, note, SOURCE);
                    raw.setMatchTitle(result.title);
                    out.add(raw);
                }
            }
            if (!out.isEmpty()) {
                log.info("[ingest] gying 命中 kw={} 结果={}", keyword, out.size());
            }
            return out;
        } catch (Exception e) {
            log.warn("[ingest] gying 搜索失败 kw={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }
}
