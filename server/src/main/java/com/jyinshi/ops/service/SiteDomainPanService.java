package com.jyinshi.ops.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.ops.dto.PanOptionVO;
import com.jyinshi.ops.dto.SiteDomainConfigVO;
import com.jyinshi.ops.dto.SiteDomainSaveRequest;
import com.jyinshi.ops.entity.SiteDomainConfig;
import com.jyinshi.ops.mapper.SiteDomainConfigMapper;
import com.jyinshi.ops.util.SiteHostResolver;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按访问域名决定前台可见网盘。未命中域名配置时回落全局 {@code pan.display.*}。
 * 采集侧仍只用全局开关，不走本服务。
 */
@Slf4j
@Service
public class SiteDomainPanService {

    private final SiteDomainConfigMapper mapper;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    /** host → pans flags（仅 enabled=1 的行） */
    private final ConcurrentHashMap<String, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public SiteDomainPanService(SiteDomainConfigMapper mapper,
                                SysConfigService sysConfigService,
                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        reloadCache();
    }

    public synchronized void reloadCache() {
        cache.clear();
        List<SiteDomainConfig> rows = mapper.selectList(Wrappers.<SiteDomainConfig>lambdaQuery()
                .eq(SiteDomainConfig::getEnabled, 1));
        for (SiteDomainConfig row : rows) {
            String host = SiteHostResolver.normalize(row.getHost());
            if (host == null) {
                continue;
            }
            cache.put(host, parsePans(row.getPansJson()));
        }
        log.info("[站点网盘] 已加载 {} 条域名配置", cache.size());
    }

    public List<PanOptionVO> panOptions() {
        List<PanOptionVO> out = new ArrayList<>();
        for (String[] pan : sysConfigService.panDisplayOptions()) {
            out.add(new PanOptionVO(pan[1], pan[0]));
        }
        return out;
    }

    public List<SiteDomainConfigVO> listAll() {
        List<SiteDomainConfig> rows = mapper.selectList(Wrappers.<SiteDomainConfig>lambdaQuery()
                .orderByAsc(SiteDomainConfig::getId));
        List<SiteDomainConfigVO> out = new ArrayList<>();
        for (SiteDomainConfig row : rows) {
            out.add(toVo(row));
        }
        return out;
    }

    @Transactional
    public SiteDomainConfigVO create(SiteDomainSaveRequest req) {
        String host = requireHost(req.getHost());
        if (mapper.selectCount(Wrappers.<SiteDomainConfig>lambdaQuery()
                .eq(SiteDomainConfig::getHost, host)) > 0) {
            throw new BizException("域名已存在：" + host);
        }
        SiteDomainConfig row = new SiteDomainConfig();
        row.setHost(host);
        row.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        row.setPansJson(toJson(normalizePans(req.getPans())));
        row.setRemark(trimRemark(req.getRemark()));
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        mapper.insert(row);
        reloadCache();
        return toVo(row);
    }

    @Transactional
    public SiteDomainConfigVO update(Long id, SiteDomainSaveRequest req) {
        SiteDomainConfig row = mapper.selectById(id);
        if (row == null) {
            throw new BizException("域名配置不存在");
        }
        String host = requireHost(req.getHost());
        Long dup = mapper.selectCount(Wrappers.<SiteDomainConfig>lambdaQuery()
                .eq(SiteDomainConfig::getHost, host)
                .ne(SiteDomainConfig::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException("域名已存在：" + host);
        }
        row.setHost(host);
        row.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        row.setPansJson(toJson(normalizePans(req.getPans())));
        row.setRemark(trimRemark(req.getRemark()));
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        reloadCache();
        return toVo(row);
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException("域名配置不存在");
        }
        mapper.deleteById(id);
        reloadCache();
    }

    /** 前台 label 列表：命中域名用域名配置，否则全局 pan.display。 */
    public List<String> enabledPanLabels(HttpServletRequest request) {
        Map<String, Boolean> flags = flagsForRequest(request);
        if (flags != null) {
            return sysConfigService.enabledPanLabelsFromFlags(flags);
        }
        return sysConfigService.enabledPanLabels();
    }

    /** 搜链过滤用 pan_type：须在请求线程调用（SSE 异步前先解析好传入）。 */
    public Set<String> enabledPanTypes(HttpServletRequest request) {
        Map<String, Boolean> flags = flagsForRequest(request);
        if (flags != null) {
            return sysConfigService.enabledPanTypesFromFlags(flags);
        }
        return sysConfigService.enabledPanTypes();
    }

    public Set<String> enabledPanTypesForHost(String host) {
        Map<String, Boolean> flags = flagsForHost(host);
        if (flags != null) {
            return sysConfigService.enabledPanTypesFromFlags(flags);
        }
        return sysConfigService.enabledPanTypes();
    }

    private Map<String, Boolean> flagsForRequest(HttpServletRequest request) {
        return flagsForHost(SiteHostResolver.resolve(request));
    }

    private Map<String, Boolean> flagsForHost(String host) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        return cache.get(host);
    }

    private SiteDomainConfigVO toVo(SiteDomainConfig row) {
        SiteDomainConfigVO vo = new SiteDomainConfigVO();
        vo.setId(row.getId());
        vo.setHost(row.getHost());
        vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        vo.setPans(normalizePans(parsePans(row.getPansJson())));
        vo.setRemark(row.getRemark());
        return vo;
    }

    private Map<String, Boolean> normalizePans(Map<String, Boolean> raw) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String[] pan : sysConfigService.panDisplayOptions()) {
            boolean on = raw != null && Boolean.TRUE.equals(raw.get(pan[1]));
            out.put(pan[1], on);
        }
        return out;
    }

    private Map<String, Boolean> parsePans(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Boolean>>() {
            });
        } catch (Exception e) {
            log.warn("[站点网盘] pans_json 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Boolean> pans) {
        try {
            return objectMapper.writeValueAsString(pans);
        } catch (Exception e) {
            throw new BizException("网盘开关序列化失败");
        }
    }

    private static String requireHost(String raw) {
        String host = SiteHostResolver.normalize(raw);
        if (host == null) {
            throw new BizException("域名无效，请填写如 naspt.vip");
        }
        return host;
    }

    private static String trimRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String t = remark.trim();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }
}
