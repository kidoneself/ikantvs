package com.jyinshi.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 转存失败码分类：链接/分享本身失效 vs 账号/环境不可用。
 * 前者应标记 media_link.invalid，后者不应误杀链接。
 */
final class TransferLinkFailureCodes {

    /**
     * 分享链“失效/不可用”→ 标记 media_link.invalid 并写全局失效黑名单，做到“越用越有效”：
     *   INVALID_URL   链接本身不合法
     *   SHARE_INVALID 网盘返回终态 errno（已过期/已取消/已删除）
     *   NO_FILES      分享为空 / 过滤后无有效文件
     *   VERIFY_FAILED 提取码/访问校验过不去（百度等明确校验失败）
     * 这些都是“针对这条分享”反复失败、换个入口也一样死的情况，标失效后再采/再搜不再出现。
     *
     * <p>注意：{@code TOKEN_FAILED}/{@code DETAIL_FAILED}/{@code LIST_FAILED} 在夸克侧常是
     * 网络抖动/限流/瞬时失败（见 QuarkDriver 注释），不得误杀链接。
     * {@link #ACCOUNT_FAILURE}（我方没账号/cookie 过期）也绝不据此标失效。</p>
     */
    static final Set<String> LINK_FAILURE = Set.of(
            "INVALID_URL", "SHARE_INVALID", "NO_FILES", "VERIFY_FAILED");

    /** 账号/本机环境类硬失败：快速结束任务，但绝不标记链接失效（本站侧问题，非资源死）。 */
    static final Set<String> ACCOUNT_FAILURE = Set.of(
            "NO_ACCOUNT", "NO_COOKIE", "AUTH_FAILED", "UNSUPPORTED");

    private TransferLinkFailureCodes() {
    }

    static boolean isLinkFailure(String errorCode) {
        return StringUtils.hasText(errorCode) && LINK_FAILURE.contains(errorCode);
    }

    static String extractErrorCode(ObjectMapper mapper, String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(resultJson).get("errorCode");
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
