package com.jyinshi.content.ingest;

import com.jyinshi.ops.service.SysConfigService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采集侧网盘类型过滤：后台 {@code pan.display.*} 与 yml {@code cloud-types} 取交集。
 *
 * <p>后台全关的网盘不会传给 pansou/seedhub，也不会入库，避免无效传输。
 */
@Component
public class IngestPanFilter {

    private final SysConfigService sysConfig;

    public IngestPanFilter(SysConfigService sysConfig) {
        this.sysConfig = sysConfig;
    }

    /**
     * 有效允许的 pan_type 集合。
     *
     * @return {@code null} 表示不过滤（yml 留空且后台全开，保持历史行为）
     */
    public Set<String> resolve(String ymlCsv) {
        Set<String> enabled = sysConfig.enabledPanTypes();
        Set<String> yml = parseCsv(ymlCsv);
        if (yml != null) {
            yml.retainAll(enabled);
            return yml;
        }
        return sysConfig.isAllPansEnabled() ? null : enabled;
    }

    /** 供 pansou {@code cloud_types} 使用；{@code null} 集合时返回空串（不传=全要）。 */
    public String toCsv(String ymlCsv) {
        Set<String> allowed = resolve(ymlCsv);
        return allowed == null ? "" : String.join(",", allowed);
    }

    public boolean allows(String panType, String ymlCsv) {
        if (!StringUtils.hasText(panType)) {
            return false;
        }
        String pan = panType.trim().toLowerCase();
        Set<String> allowed = resolve(ymlCsv);
        if (allowed == null) {
            return sysConfig.isPanTypeAllowed(pan);
        }
        return allowed.contains(pan);
    }

    private static Set<String> parseCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return null;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
