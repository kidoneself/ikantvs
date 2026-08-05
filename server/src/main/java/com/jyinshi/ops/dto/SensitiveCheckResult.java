package com.jyinshi.ops.dto;

import java.util.List;

/**
 * 敏感词检测结果。供搜索/展示等入口决策用，也作为后台「在线测试」返回。
 *
 * @param hit     是否命中任意敏感词
 * @param words   命中的词（去重）
 * @param action  命中词里最严的动作（block&gt;review&gt;replace&gt;warn）；未命中为 null
 * @param blocked action 是否为 block（命中即应拦截的便捷判断）
 * @param filtered 命中处打码（替换为 *）后的文本，便于后台预览效果
 */
public record SensitiveCheckResult(
        boolean hit,
        List<String> words,
        String action,
        boolean blocked,
        String filtered) {
}
