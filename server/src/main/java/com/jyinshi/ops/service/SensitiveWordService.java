package com.jyinshi.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.event.SensitiveWordsReloadedEvent;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.ops.dto.SensitiveCheckResult;
import com.jyinshi.ops.dto.SensitiveWordBatchRequest;
import com.jyinshi.ops.dto.SensitiveWordSaveRequest;
import com.jyinshi.ops.dto.SensitiveWordVO;
import com.jyinshi.ops.entity.SensitiveWord;
import com.jyinshi.ops.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词——文本检测能力的唯一出口（ops 域）。
 *
 * <p>用途：搜索词拦截、内容发布门槛、前台读路径实时过滤（列表/详情/网盘 note 等）。
 * 其它域要检测文本统一调本服务，不要各自实现（架构铁律 3：跨域只调对方 service）。
 *
 * <p>实现照搬 {@link SysConfigService} 的缓存范式：启用的词在启动时构建成 DFA（确定有限自动机/
 * 前缀树）常驻内存，一次扫描即可多模式匹配；增删改后 {@link #reload()} 重建，运行时即时生效。
 */
@Slf4j
@Service
public class SensitiveWordService {

    // ---- 命中动作（按严重度由高到低）----
    public static final String ACTION_BLOCK = "block";     // 拦截：搜索返回空 / 内容不发布
    public static final String ACTION_REVIEW = "review";   // 转人工审核
    public static final String ACTION_REPLACE = "replace"; // 打码后展示
    public static final String ACTION_WARN = "warn";       // 仅标记，不拦
    private static final List<String> ACTIONS = List.of(ACTION_BLOCK, ACTION_REVIEW, ACTION_REPLACE, ACTION_WARN);

    // ---- 分类 ----
    public static final List<String> CATEGORIES =
            List.of("politics", "porn", "ad", "violence", "legacy", "other");

    private final SensitiveWordMapper mapper;
    private final ApplicationEventPublisher events;

    /** DFA 树 + 词→动作 映射，整体不可变；reload 时原子替换引用，读取无需加锁。 */
    private volatile Matcher matcher = new Matcher(new Node(), Map.of());

    public SensitiveWordService(SensitiveWordMapper mapper, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.events = events;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 重建内存 DFA（增删改词后调用）。 */
    public void reload() {
        List<SensitiveWord> words = mapper.selectList(
                Wrappers.<SensitiveWord>lambdaQuery().eq(SensitiveWord::getEnabled, 1));
        Node root = new Node();
        Map<String, String> actions = new HashMap<>();
        for (SensitiveWord w : words) {
            String norm = normalize(w.getWord());
            if (norm.isEmpty()) {
                continue;
            }
            addWord(root, norm);
            // 同词多条时取更严的动作
            actions.merge(norm, safeAction(w.getAction()), this::moreSevere);
        }
        this.matcher = new Matcher(root, actions);
        log.info("敏感词已加载，启用 {} 条", actions.size());
        events.publishEvent(new SensitiveWordsReloadedEvent());
    }

    // ---------------- 检测能力（跨域调用入口）----------------

    /** 命中任意敏感词即 true。 */
    public boolean contains(String text) {
        return !matcher.match(normalize(text)).isEmpty();
    }

    /** 命中即应拦截（命中词里有 block）。搜索/发布门槛用这个。 */
    public boolean isBlocked(String text) {
        SensitiveCheckResult r = check(text);
        return r.blocked();
    }

    /**
     * 当前启用的 block 级敏感词集合（已归一化）。
     * 供前台读路径在 SQL 层直接排除命中 block 的标题，保证分页 total 与实际返回条数一致。
     */
    public Set<String> blockWords() {
        Matcher m = this.matcher;
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : m.actions().entrySet()) {
            if (ACTION_BLOCK.equals(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 把命中处替换为等长 *。 */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String norm = normalize(text);
        List<int[]> hits = matcher.match(norm);
        if (hits.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int[] hit : hits) {
            for (int i = hit[0]; i <= hit[1] && i < chars.length; i++) {
                chars[i] = '*';
            }
        }
        return new String(chars);
    }

    /** 完整检测：命中词 + 最严动作 + 打码预览。供搜索/发布决策与后台测试用。 */
    public SensitiveCheckResult check(String text) {
        if (text == null || text.isBlank()) {
            return new SensitiveCheckResult(false, List.of(), null, false, text);
        }
        String norm = normalize(text);
        List<int[]> hits = matcher.match(norm);
        if (hits.isEmpty()) {
            return new SensitiveCheckResult(false, List.of(), null, false, text);
        }
        Set<String> words = new LinkedHashSet<>();
        String worst = ACTION_WARN;
        for (int[] hit : hits) {
            String word = norm.substring(hit[0], hit[1] + 1);
            words.add(word);
            worst = moreSevere(worst, matcher.actions().getOrDefault(word, ACTION_WARN));
        }
        return new SensitiveCheckResult(true, new ArrayList<>(words), worst,
                ACTION_BLOCK.equals(worst), filter(text));
    }

    // ---------------- 后台管理 ----------------

    public PageResult<SensitiveWordVO> page(long page, long size, String category, String action, String keyword) {
        LambdaQueryWrapper<SensitiveWord> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(category)) {
            w.eq(SensitiveWord::getCategory, category.trim());
        }
        if (StringUtils.hasText(action)) {
            w.eq(SensitiveWord::getAction, action.trim());
        }
        if (StringUtils.hasText(keyword)) {
            w.like(SensitiveWord::getWord, normalize(keyword));
        }
        w.orderByDesc(SensitiveWord::getId);
        IPage<SensitiveWord> p = mapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size,
                p.getRecords().stream().map(SensitiveWordVO::from).toList());
    }

    @Transactional
    public SensitiveWordVO create(SensitiveWordSaveRequest req) {
        String word = normalize(req.getWord());
        if (word.isEmpty()) {
            throw new BizException("词不能为空");
        }
        if (mapper.exists(Wrappers.<SensitiveWord>lambdaQuery().eq(SensitiveWord::getWord, word))) {
            throw new BizException("该词已存在：" + word);
        }
        SensitiveWord e = new SensitiveWord();
        e.setWord(word);
        e.setCategory(safeCategory(req.getCategory()));
        e.setAction(req.getAction() != null ? safeAction(req.getAction()) : ACTION_BLOCK);
        e.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
        e.setRemark(req.getRemark());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        mapper.insert(e);
        reload();
        return SensitiveWordVO.from(e);
    }

    @Transactional
    public SensitiveWordVO update(Long id, SensitiveWordSaveRequest req) {
        SensitiveWord e = mapper.selectById(id);
        if (e == null) {
            throw new BizException("敏感词不存在");
        }
        String word = normalize(req.getWord());
        if (word.isEmpty()) {
            throw new BizException("词不能为空");
        }
        if (!word.equals(e.getWord())
                && mapper.exists(Wrappers.<SensitiveWord>lambdaQuery().eq(SensitiveWord::getWord, word))) {
            throw new BizException("该词已存在：" + word);
        }
        e.setWord(word);
        e.setCategory(safeCategory(req.getCategory()));
        if (req.getAction() != null) {
            e.setAction(safeAction(req.getAction()));
        }
        if (req.getEnabled() != null) {
            e.setEnabled(req.getEnabled() ? 1 : 0);
        }
        e.setRemark(req.getRemark());
        e.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(e);
        reload();
        return SensitiveWordVO.from(e);
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException("敏感词不存在");
        }
        reload();
    }

    /**
     * 批量导入：一行一个词，整批共用 category/action。已存在的词跳过。
     * 默认 category=legacy、action=warn（迁入/批量先当观察名单）。
     *
     * @return 实际新增条数
     */
    @Transactional
    public int importBatch(SensitiveWordBatchRequest req) {
        String category = StringUtils.hasText(req.getCategory()) ? safeCategory(req.getCategory()) : "legacy";
        String action = StringUtils.hasText(req.getAction()) ? safeAction(req.getAction()) : ACTION_WARN;

        // 去重 + 归一化
        Set<String> incoming = new LinkedHashSet<>();
        for (String line : req.getText().split("\\r?\\n")) {
            String norm = normalize(line);
            if (!norm.isEmpty() && norm.length() <= 64) {
                incoming.add(norm);
            }
        }
        if (incoming.isEmpty()) {
            return 0;
        }
        // 已存在的跳过
        Set<String> existing = new LinkedHashSet<>();
        for (SensitiveWord w : mapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .select(SensitiveWord::getWord)
                .in(SensitiveWord::getWord, incoming))) {
            existing.add(w.getWord());
        }
        int added = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String word : incoming) {
            if (existing.contains(word)) {
                continue;
            }
            SensitiveWord e = new SensitiveWord();
            e.setWord(word);
            e.setCategory(category);
            e.setAction(action);
            e.setEnabled(1);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            mapper.insert(e);
            added++;
        }
        if (added > 0) {
            reload();
        }
        return added;
    }

    public long count() {
        return mapper.selectCount(null);
    }

    // ---------------- 内部 ----------------

    /** 归一化：去首尾空白 + 转小写（中文不受影响，英文便于匹配）。 */
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String safeAction(String a) {
        String v = a == null ? "" : a.trim().toLowerCase();
        return ACTIONS.contains(v) ? v : ACTION_BLOCK;
    }

    private String safeCategory(String c) {
        String v = c == null ? "" : c.trim().toLowerCase();
        return CATEGORIES.contains(v) ? v : "other";
    }

    /** 取更严的动作（block&gt;review&gt;replace&gt;warn，下标越小越严）。 */
    private String moreSevere(String a, String b) {
        return ACTIONS.indexOf(a) <= ACTIONS.indexOf(b) ? a : b;
    }

    private static void addWord(Node root, String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.computeIfAbsent(word.charAt(i), k -> new Node());
        }
        node.end = true;
    }

    /** DFA 节点。 */
    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        boolean end;
    }

    /** 不可变匹配器：DFA 根 + 词→动作。 */
    private record Matcher(Node root, Map<String, String> actions) {

        /** 最长不重叠匹配，返回命中区间 [start, endInclusive]。 */
        List<int[]> match(String text) {
            List<int[]> hits = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                return hits;
            }
            int n = text.length();
            for (int i = 0; i < n; i++) {
                Node node = root.children.get(text.charAt(i));
                if (node == null) {
                    continue;
                }
                int matchEnd = -1;
                int j = i;
                while (node != null) {
                    if (node.end) {
                        matchEnd = j;
                    }
                    if (++j >= n) {
                        break;
                    }
                    node = node.children.get(text.charAt(j));
                }
                if (matchEnd >= 0) {
                    hits.add(new int[]{i, matchEnd});
                    i = matchEnd;
                }
            }
            return hits;
        }
    }
}
