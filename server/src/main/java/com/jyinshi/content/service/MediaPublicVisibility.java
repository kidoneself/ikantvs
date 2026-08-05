package com.jyinshi.content.service;

import com.jyinshi.content.entity.Media;
import com.jyinshi.ops.service.SensitiveWordService;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 前台内容是否可见：已发布、已上映/开播、未运营隐藏、<b>标题</b>未命中 block 级敏感词。
 * 列表/搜索/榜单/详情/链接等读路径统一口径。
 *
 * <p>注意：只有「标题」本身命中 block 才整条隐藏（如违禁片名）。「简介」里的敏感词<b>不</b>隐藏整条，
 * 改为在展示时用 {@link #maskOverview} 打码——避免正常影视（如谍战/历史剧）因剧情简介提及历史词汇被误杀。
 */
final class MediaPublicVisibility {

    private MediaPublicVisibility() {
    }

    static boolean isVisible(Media m, SensitiveWordService sensitiveWordService) {
        if (m == null || m.getPubStatus() == null || m.getPubStatus() != 1) {
            return false;
        }
        if (m.getSearchHidden() != null && m.getSearchHidden() == 1) {
            return false;
        }
        if (StringUtils.hasText(m.getReleaseDate())
                && m.getReleaseDate().compareTo(LocalDate.now().toString()) > 0) {
            return false;
        }
        return !sensitiveWordService.isBlocked(m.getTitle());
    }

    /** 前台展示前：把简介里命中的敏感词打码为等长 {@code *}。就地修改内存对象，不落库。 */
    static void maskOverview(Media m, SensitiveWordService sensitiveWordService) {
        if (m != null && StringUtils.hasText(m.getOverview())) {
            m.setOverview(sensitiveWordService.filter(m.getOverview()));
        }
    }
}
