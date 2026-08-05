package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.entity.InvalidShare;
import com.jyinshi.content.mapper.InvalidShareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 失效分享黑名单服务（content 域）。
 *
 * <p>两个用途：
 * <ul>
 *   <li>采集入库前批量过滤——命中黑名单的 share 直接不入库（等价老系统 filterInvalidLinks）；</li>
 *   <li>转存首转因链接失效失败时回写——越用越准。</li>
 * </ul>
 * 只收「链接本身失效」（分享删除/取消/无权限等），账号侧失败不进这里。
 */
@Slf4j
@Service
public class InvalidShareService {

    private final InvalidShareMapper invalidShareMapper;

    public InvalidShareService(InvalidShareMapper invalidShareMapper) {
        this.invalidShareMapper = invalidShareMapper;
    }

    /**
     * 批量查出「已知失效」的 shareId 子集（供采集入库前过滤）。
     *
     * @param panType  网盘类型（小写）
     * @param shareIds 本轮候选的 shareId 集合
     * @return 其中命中黑名单的 shareId（可直接跳过入库）
     */
    public Set<String> knownInvalid(String panType, Collection<String> shareIds) {
        if (!StringUtils.hasText(panType) || shareIds == null || shareIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> distinct = shareIds.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return invalidShareMapper.selectList(Wrappers.<InvalidShare>lambdaQuery()
                            .select(InvalidShare::getShareId)
                            .eq(InvalidShare::getPanType, panType)
                            .in(InvalidShare::getShareId, distinct))
                    .stream()
                    .map(InvalidShare::getShareId)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            // 查询异常不阻断采集：宁可多采不误伤（下游转存仍会判死兜底）
            log.warn("[content] 失效黑名单批量查询失败，本轮不过滤: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 单条判断是否已知失效。 */
    public boolean isInvalid(String panType, String shareId) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(shareId)) {
            return false;
        }
        return !knownInvalid(panType, Collections.singletonList(shareId)).isEmpty();
    }

    /**
     * 记入黑名单（幂等）。转存首转确认链接失效时调用。
     *
     * @param panType   网盘类型
     * @param shareId   规范化 shareId
     * @param errorCode 错误码（可空）
     * @param reason    原因摘要（可空）
     */
    public void mark(String panType, String shareId, String errorCode, String reason) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(shareId)) {
            return;
        }
        try {
            invalidShareMapper.insertIgnore(panType.toLowerCase(), shareId,
                    truncate(errorCode, 50), truncate(reason, 255));
        } catch (Exception e) {
            log.warn("[content] 写入失效黑名单失败 pan={}, share={}: {}", panType, shareId, e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
