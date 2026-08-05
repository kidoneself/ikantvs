package com.jyinshi.search.docmonitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单任务可配置的剧目聚合规则。存在 {@code doc_monitor_task.parse_rules}，改规则无需发版。
 *
 * <p>状态机：非夸克/百度/迅雷/噪声行 → 新剧名；匹配对应前缀且行内有链 → 挂到当前剧。
 */
@Data
public class ParseRules {

    /** 模板标识（仅展示/回填用）：flowus-default / kdocs-default / custom */
    private String template;

    /** 夸克行前缀，如 夸盘、KK、夸克 */
    private List<String> quarkPrefixes = new ArrayList<>();

    /** 百度行前缀，如 度盘、BD、百度 */
    private List<String> baiduPrefixes = new ArrayList<>();

    /** 迅雷行前缀，如 迅雷、雷盘、XL */
    private List<String> xunleiPrefixes = new ArrayList<>();

    /** 噪声行：整行以此开头或包含（见 {@link #noiseContains}）则跳过 */
    private List<String> noisePrefixes = new ArrayList<>();

    /** 噪声行：文本包含任一即跳过 */
    private List<String> noiseContains = new ArrayList<>();

    /**
     * 从剧名行提取短名的正则（可含多个捕获组，取第一个非空）。
     * 默认：书名号/直角引号内。
     */
    private String nameExtractRegex = "[「《]([^」》]+)[」》]";

    /** 段内提取码，合并到未带 pwd 的百度链。 */
    private String pwdRegex = "提取码\\s*[:：]\\s*([a-zA-Z0-9]+)";

    /**
     * 前缀匹配模式：
     * <ul>
     *   <li>{@code startsWith}：忽略大小写，行首匹配前缀（FlowUs 友好）</li>
     *   <li>{@code labeled}：前缀后跟可选空白与 {@code :/：}，且需行内有对应网盘链（kdocs 友好）</li>
     * </ul>
     */
    private String matchMode = "startsWith";
}
