package com.jyinshi.ops.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.ops.dto.LiveQrcodeAdminVO;
import com.jyinshi.ops.dto.LiveQrcodeUpdateRequest;
import com.jyinshi.ops.dto.SitePublicConfigVO;
import com.jyinshi.ops.entity.LiveQrcodeConfig;
import com.jyinshi.ops.entity.LiveQrcodeLog;
import com.jyinshi.ops.mapper.LiveQrcodeConfigMapper;
import com.jyinshi.ops.mapper.LiveQrcodeLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 活码 / 站内加群（ops）：群码可随时换图；公众号单独上传。
 */
@Service
public class LiveQrcodeService {

    private final LiveQrcodeConfigMapper configMapper;
    private final LiveQrcodeLogMapper logMapper;

    public LiveQrcodeService(LiveQrcodeConfigMapper configMapper, LiveQrcodeLogMapper logMapper) {
        this.configMapper = configMapper;
        this.logMapper = logMapper;
    }

    public LiveQrcodeConfig requireConfig() {
        LiveQrcodeConfig c = configMapper.selectById(1L);
        if (c == null) {
            c = new LiveQrcodeConfig();
            c.setId(1L);
            c.setTitle("防止失联");
            c.setTipText("长按识别二维码，加入交流群");
            c.setScanCount(0);
            c.setStatus(1);
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(c);
        }
        return c;
    }

    public LiveQrcodeAdminVO adminConfig() {
        LiveQrcodeConfig c = requireConfig();
        LiveQrcodeAdminVO vo = new LiveQrcodeAdminVO();
        vo.setQrcodeImage(nullToEmpty(c.getQrcodeImage()));
        vo.setMpQrcodeImage(nullToEmpty(c.getMpQrcodeImage()));
        vo.setTitle(c.getTitle());
        vo.setTipText(c.getTipText());
        vo.setScanCount(c.getScanCount() == null ? 0 : c.getScanCount());
        vo.setStatus(c.getStatus() == null ? 0 : c.getStatus());
        return vo;
    }

    public void updateConfig(LiveQrcodeUpdateRequest req) {
        LiveQrcodeConfig c = requireConfig();
        if (req.getQrcodeImage() != null) {
            c.setQrcodeImage(req.getQrcodeImage().trim());
        }
        if (req.getMpQrcodeImage() != null) {
            c.setMpQrcodeImage(req.getMpQrcodeImage().trim());
        }
        if (req.getTitle() != null) {
            String t = req.getTitle().trim();
            c.setTitle(t.isEmpty() ? "防止失联" : t);
        }
        if (req.getTipText() != null) {
            c.setTipText(req.getTipText().trim());
        }
        if (req.getStatus() != null) {
            if (req.getStatus() != 0 && req.getStatus() != 1) {
                throw new BizException("状态无效");
            }
            c.setStatus(req.getStatus());
        }
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(c);
    }

    /** 填充前台站点配置中的联系字段。 */
    public void fillContact(SitePublicConfigVO vo) {
        LiveQrcodeConfig c = requireConfig();
        boolean on = c.getStatus() != null && c.getStatus() == 1;
        vo.setContactEnabled(on);
        vo.setContactTitle(StringUtils.hasText(c.getTitle()) ? c.getTitle() : "防止失联");
        vo.setContactTip(nullToEmpty(c.getTipText()));
        vo.setContactGroupQrcode(on ? nullToEmpty(c.getQrcodeImage()) : "");
        vo.setContactMpQrcode(on ? nullToEmpty(c.getMpQrcodeImage()) : "");
    }

    /**
     * 活码页公开数据（对齐老站 GET /api/qr）；禁用时抛业务异常。
     */
    @Transactional
    public Map<String, Object> openQrPage(String from, String ip, String userAgent) {
        LiveQrcodeConfig c = requireConfig();
        if (c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException("活码已禁用");
        }
        LiveQrcodeLog log = new LiveQrcodeLog();
        log.setSource(from != null ? from.trim() : "");
        log.setIp(ip);
        log.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);

        int count = c.getScanCount() == null ? 0 : c.getScanCount();
        c.setScanCount(count + 1);
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(c);

        Map<String, Object> data = new HashMap<>();
        data.put("qrcodeImage", nullToEmpty(c.getQrcodeImage()));
        data.put("mpQrcodeImage", nullToEmpty(c.getMpQrcodeImage()));
        data.put("title", StringUtils.hasText(c.getTitle()) ? c.getTitle() : "防止失联");
        data.put("tipText", nullToEmpty(c.getTipText()));
        data.put("scanCount", count + 1);
        return data;
    }

    public Map<String, Object> stats() {
        LiveQrcodeConfig c = requireConfig();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", c.getScanCount() == null ? 0 : c.getScanCount());
        stats.put("todayCount", logMapper.countToday());
        stats.put("sourceStats", logMapper.countBySource());
        stats.put("trendStats", logMapper.countByDateRecent(7));
        return stats;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
