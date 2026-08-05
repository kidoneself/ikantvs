package com.jyinshi.ops.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.ops.dto.SysConfigItemVO;
import com.jyinshi.ops.dto.SitePublicConfigVO;
import com.jyinshi.ops.entity.SysConfig;
import com.jyinshi.ops.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置——运行时可改的全局开关/参数的唯一出口（ops 域）。
 *
 * <p>数据存 {@code sys_config} 表，启动时载入内存缓存；写入即更新缓存，运行时即时生效。
 * 首次启动按 application.yml/env 的值做种子（仅当键缺失时），之后以库为准。
 * 其它域要读这些开关，统一调本服务，不要再各自读 yml。
 */
@Slf4j
@Service
public class SysConfigService {

    public static final String XUNLEI_SDK_ENABLED = "xunlei.sdk.enabled";
    /** 迅雷推广达人数字账号（SDK extra.custom，结算必填）。 */
    public static final String XUNLEI_PARTNER_CUSTOM = "xunlei.partner.custom";

    // 元数据采集（TMDB）——运行时可在后台修改；国内部署把 base-url 指向反代即可。
    public static final String META_TMDB_API_KEY = "metadata.tmdb.api-key";
    public static final String META_TMDB_BASE_URL = "metadata.tmdb.base-url";
    public static final String META_TMDB_IMAGE_BASE = "metadata.tmdb.image-base";
    public static final String META_TMDB_BACKDROP_BASE = "metadata.tmdb.backdrop-base";
    public static final String META_TMDB_LANGUAGE = "metadata.tmdb.language";
    public static final String META_TMDB_TIMEOUT_MS = "metadata.tmdb.timeout-ms";

    // 元数据采集（豆瓣补录）。
    public static final String META_DOUBAN_ENABLED = "metadata.douban.enabled";
    public static final String META_DOUBAN_APIKEY = "metadata.douban.apikey";
    public static final String META_DOUBAN_SECRET = "metadata.douban.secret";
    public static final String META_DOUBAN_FRODO_BASE = "metadata.douban.frodo-base";
    public static final String META_DOUBAN_LEGACY_APIKEY = "metadata.douban.legacy-apikey";
    public static final String META_DOUBAN_LEGACY_BASE = "metadata.douban.legacy-base";
    public static final String META_DOUBAN_TIMEOUT_MS = "metadata.douban.timeout-ms";

    // 转存·迅雷开放平台应用级配置（所有迅雷账号共用一个 app），后台可改。
    public static final String TRANSFER_XUNLEI_CLIENT_ID = "transfer.xunlei.client-id";
    public static final String TRANSFER_XUNLEI_CLIENT_SECRET = "transfer.xunlei.client-secret";
    public static final String TRANSFER_XUNLEI_DEVICE_ID = "transfer.xunlei.device-id";
    public static final String TRANSFER_XUNLEI_REDIRECT_URI = "transfer.xunlei.redirect-uri";
    /** NAS 灌盘总开关（百度监控成功后差集入队）；默认关，发版后再开。 */
    public static final String TRANSFER_NAS_ENABLED = "transfer.nas.enabled";
    /** 千云 wake 基址，如 http://nas:8234 ；空=只靠千云轮询。 */
    public static final String TRANSFER_NAS_WAKE_URL = "transfer.nas.wake-url";

    // 资源采集·pansou 来源——地址随部署环境变（国内部署指向海外反代），后台可改。
    public static final String INGEST_PANSOU_ENABLED = "ingest.pansou.enabled";
    public static final String INGEST_PANSOU_BASE_URL = "ingest.pansou.base-url";

    // 网站公告（前台顶栏 + 弹窗，正文支持 HTML）。
    public static final String SITE_NOTICE_ENABLED = "site.notice.enabled";
    public static final String SITE_NOTICE_TITLE = "site.notice.title";
    public static final String SITE_NOTICE_CONTENT = "site.notice.content";
    public static final String SITE_NOTICE_SHOW_ONCE = "site.notice.show-once";

    // 运维通知：默认关闭；密钥一律空，由各环境后台自填（禁止把个人飞书写进默认）。
    public static final String NOTIFY_ENABLED = "notify.enabled";
    public static final String NOTIFY_CHANNEL = "notify.channel";
    public static final String NOTIFY_FEISHU_APP_ID = "notify.feishu.app-id";
    public static final String NOTIFY_FEISHU_APP_SECRET = "notify.feishu.app-secret";
    public static final String NOTIFY_FEISHU_USER_ID = "notify.feishu.user-id";
    public static final String NOTIFY_WEBHOOK_URL = "notify.webhook.url";
    public static final String NOTIFY_WEBHOOK_SECRET = "notify.webhook.secret";
    /** 微信机器人机机调用（「今日」拉取 / 后续 propose）共用 token。 */
    public static final String NOTIFY_WECHAT_TOKEN = "notify.wechat.token";

    private static final String GROUP_TMDB = "TMDB 采集";
    private static final String GROUP_DOUBAN = "豆瓣采集";
    private static final String GROUP_XUNLEI = "迅雷转存";
    private static final String GROUP_NAS = "NAS灌盘";
    private static final String GROUP_INGEST = "资源采集";
    private static final String GROUP_NOTICE = "网站公告";
    private static final String GROUP_NOTIFY = "运维通知";

    /** 网盘展示开关前缀：pan.display.<slug>，关闭后前台不显示该网盘页签。 */
    public static final String PAN_DISPLAY_PREFIX = "pan.display.";
    /** 网盘页签顺序：逗号分隔 slug，后台可拖排；缺项自动补到末尾。 */
    public static final String PAN_DISPLAY_ORDER = "pan.display.order";
    /** 网盘展示默认顺序与 {label, 配置 slug}。 */
    private static final List<String[]> PAN_DISPLAY = List.of(
            new String[]{"磁力", "magnet"},
            new String[]{"百度", "baidu"},
            new String[]{"夸克", "quark"},
            new String[]{"迅雷", "xunlei"},
            new String[]{"UC", "uc"},
            new String[]{"阿里", "aliyun"},
            new String[]{"天翼", "tianyi"},
            new String[]{"移动", "mobile"},
            new String[]{"115", "pan115"},
            new String[]{"123", "pan123"},
            new String[]{"其他", "other"});

    private static final String DEFAULT_PAN_ORDER = PAN_DISPLAY.stream()
            .map(p -> p[1])
            .collect(Collectors.joining(","));

    /** 归入「其他」页签、无独立 pan.display 开关的网盘类型。 */
    private static final Set<String> OTHER_PAN_TYPES = Set.of("pikpak", "ed2k");

    private static final String TYPE_BOOL = "BOOL";
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_TEXTAREA = "TEXTAREA";
    private static final String TYPE_NUMBER = "NUMBER";
    private static final String TYPE_ENUM = "ENUM";
    /** 敏感文本（key/密钥），后台渲染成密码框；校验同 TEXT。 */
    private static final String TYPE_SECRET = "SECRET";

    private final SysConfigMapper mapper;
    private final Environment env;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final List<ConfigDef> schema = new ArrayList<>();

    public SysConfigService(SysConfigMapper mapper, Environment env) {
        this.mapper = mapper;
        this.env = env;
    }

    @PostConstruct
    public void init() {
        schema.add(new ConfigDef(XUNLEI_SDK_ENABLED, "迅雷 JS-SDK", "迅雷推广", TYPE_BOOL,
                null, "开启后详情页磁力等资源显示「迅雷高速下载」", "false"));
        schema.add(new ConfigDef(XUNLEI_PARTNER_CUSTOM, "迅雷达人数字账号", "迅雷推广", TYPE_TEXT,
                null, "接入文档 extra.custom，报备域名后填写；空则前台不展示迅雷下载", ""));
        schema.add(new ConfigDef(PAN_DISPLAY_ORDER, "网盘页签顺序", "网盘展示", TYPE_TEXT,
                null, "逗号分隔 slug（如 quark,baidu,magnet）；后台可拖排，前台详情/搜索页签按此顺序",
                DEFAULT_PAN_ORDER));
        for (String[] pan : PAN_DISPLAY) {
            schema.add(new ConfigDef(PAN_DISPLAY_PREFIX + pan[1], pan[0], "网盘展示", TYPE_BOOL,
                    null, "全局默认/采集用；已在「域名网盘」配置的站点以前台域名开关为准。关闭后未单独配置的域名不显示「"
                            + pan[0] + "」", "true"));
        }

        // 默认值取 application.yml / 环境变量（首次启动播种），之后以库为准、后台可改。
        schema.add(new ConfigDef(META_TMDB_API_KEY, "TMDB API Key", GROUP_TMDB, TYPE_SECRET,
                null, "TMDB v3 API Key，必填才能采集元数据",
                seed("jyinshi.metadata.tmdb.api-key", "")));
        schema.add(new ConfigDef(META_TMDB_BASE_URL, "TMDB 接口地址", GROUP_TMDB, TYPE_TEXT,
                null, "TMDB v3 接口基址；国内部署改成你的反代地址（如 https://反代域名/tmdb/3）",
                seed("jyinshi.metadata.tmdb.base-url", "https://api.themoviedb.org/3")));
        schema.add(new ConfigDef(META_TMDB_IMAGE_BASE, "海报图基址", GROUP_TMDB, TYPE_TEXT,
                null, "海报图前缀；国内部署可指向反代的图片路径",
                seed("jyinshi.metadata.tmdb.image-base", "https://image.tmdb.org/t/p/w500")));
        schema.add(new ConfigDef(META_TMDB_BACKDROP_BASE, "剧照图基址", GROUP_TMDB, TYPE_TEXT,
                null, "剧照/背景图前缀；国内部署可指向反代的图片路径",
                seed("jyinshi.metadata.tmdb.backdrop-base", "https://image.tmdb.org/t/p/w780")));
        schema.add(new ConfigDef(META_TMDB_LANGUAGE, "采集语言", GROUP_TMDB, TYPE_TEXT,
                null, "TMDB language 参数，如 zh-CN",
                seed("jyinshi.metadata.tmdb.language", "zh-CN")));
        schema.add(new ConfigDef(META_TMDB_TIMEOUT_MS, "超时(毫秒)", GROUP_TMDB, TYPE_NUMBER,
                null, "TMDB 请求超时（毫秒）",
                seed("jyinshi.metadata.tmdb.timeout-ms", "8000")));

        schema.add(new ConfigDef(META_DOUBAN_ENABLED, "启用豆瓣补录", GROUP_DOUBAN, TYPE_BOOL,
                null, "开启后用豆瓣作为元数据补录兜底",
                seed("jyinshi.metadata.douban.enabled", "true")));
        schema.add(new ConfigDef(META_DOUBAN_APIKEY, "豆瓣 apikey", GROUP_DOUBAN, TYPE_SECRET,
                null, "frodo 移动端 apikey（来自豆瓣 App，一般无需修改）",
                seed("jyinshi.metadata.douban.apikey", "")));
        schema.add(new ConfigDef(META_DOUBAN_SECRET, "豆瓣签名密钥", GROUP_DOUBAN, TYPE_SECRET,
                null, "frodo HMAC-SHA1 签名密钥",
                seed("jyinshi.metadata.douban.secret", "")));
        schema.add(new ConfigDef(META_DOUBAN_FRODO_BASE, "frodo 接口地址", GROUP_DOUBAN, TYPE_TEXT,
                null, "豆瓣 frodo 接口基址",
                seed("jyinshi.metadata.douban.frodo-base", "https://frodo.douban.com/api/v2")));
        schema.add(new ConfigDef(META_DOUBAN_LEGACY_APIKEY, "豆瓣旧版 apikey", GROUP_DOUBAN, TYPE_SECRET,
                null, "api.douban.com 旧版 apikey（IMDb 反查 subject id 用）",
                seed("jyinshi.metadata.douban.legacy-apikey", "0ab215a8b1977939201640fa14c66bab")));
        schema.add(new ConfigDef(META_DOUBAN_LEGACY_BASE, "豆瓣旧版接口地址", GROUP_DOUBAN, TYPE_TEXT,
                null, "api.douban.com 旧版接口基址",
                seed("jyinshi.metadata.douban.legacy-base", "https://api.douban.com/v2")));
        schema.add(new ConfigDef(META_DOUBAN_TIMEOUT_MS, "超时(毫秒)", GROUP_DOUBAN, TYPE_NUMBER,
                null, "豆瓣请求超时（毫秒）",
                seed("jyinshi.metadata.douban.timeout-ms", "8000")));

        schema.add(new ConfigDef(TRANSFER_XUNLEI_CLIENT_ID, "迅雷 client_id", GROUP_XUNLEI, TYPE_SECRET,
                null, "迅雷开放平台 client_id（转存/追更迅雷账号必填）",
                seed("jyinshi.transfer.xunlei.client-id", "")));
        schema.add(new ConfigDef(TRANSFER_XUNLEI_CLIENT_SECRET, "迅雷 client_secret", GROUP_XUNLEI, TYPE_SECRET,
                null, "迅雷开放平台 client_secret（OAuth 授权换 refresh_token 用）",
                seed("jyinshi.transfer.xunlei.client-secret", "")));
        schema.add(new ConfigDef(TRANSFER_XUNLEI_DEVICE_ID, "迅雷 device_id", GROUP_XUNLEI, TYPE_TEXT,
                null, "x-device-id，任意稳定字符串即可",
                seed("jyinshi.transfer.xunlei.device-id", "jyinshi")));
        schema.add(new ConfigDef(TRANSFER_XUNLEI_REDIRECT_URI, "迅雷回调地址", GROUP_XUNLEI, TYPE_TEXT,
                null, "OAuth redirect_uri，须与迅雷开放平台登记完全一致",
                seed("jyinshi.transfer.xunlei.redirect-uri", "")));

        schema.add(new ConfigDef(TRANSFER_NAS_ENABLED, "启用 NAS 灌盘", GROUP_NAS, TYPE_BOOL,
                null, "百度监控成功后灌迅雷：落地夹空则首灌，有货后只追新（可经千云）",
                "false"));
        schema.add(new ConfigDef(TRANSFER_NAS_WAKE_URL, "千云 wake 地址", GROUP_NAS, TYPE_TEXT,
                null, "如 http://nas-host:8234 ；空则只靠千云轮询认领",
                ""));

        schema.add(new ConfigDef(INGEST_PANSOU_ENABLED, "启用 pansou 采集", GROUP_INGEST, TYPE_BOOL,
                null, "关闭后不调 pansou 搜网盘资源",
                seed("jyinshi.ingest.pansou.enabled", "true")));
        schema.add(new ConfigDef(INGEST_PANSOU_BASE_URL, "pansou 接口地址", GROUP_INGEST, TYPE_TEXT,
                null, "pansou 服务基址；国内部署指向海外反代（如 https://pansou.example.com）",
                seed("jyinshi.ingest.pansou.base-url", "http://pansou")));

        schema.add(new ConfigDef(SITE_NOTICE_ENABLED, "启用公告", GROUP_NOTICE, TYPE_BOOL,
                null, "关闭后前台不显示顶栏公告与弹窗", "false"));
        schema.add(new ConfigDef(SITE_NOTICE_TITLE, "公告标题", GROUP_NOTICE, TYPE_TEXT,
                null, "弹窗标题，如「网站公告」", "网站公告"));
        schema.add(new ConfigDef(SITE_NOTICE_CONTENT, "公告正文(HTML)", GROUP_NOTICE, TYPE_TEXTAREA,
                null, "支持 HTML。群码/公众号请到「活码/加群」上传；正文进群按钮：<a data-action=\"open-contact\">加群</a>", ""));
        schema.add(new ConfigDef(SITE_NOTICE_SHOW_ONCE, "关闭后不再弹出", GROUP_NOTICE, TYPE_BOOL,
                null, "开启后用户关闭一次即记住；改正文后会重新弹出", "false"));

        schema.add(new ConfigDef(NOTIFY_ENABLED, "启用运维通知", GROUP_NOTIFY, TYPE_BOOL,
                null, "账号失效、追更失效、追更更新等推送（默认关；交付给别人的环境务必保持关闭）", "false"));
        schema.add(new ConfigDef(NOTIFY_CHANNEL, "通知通道", GROUP_NOTIFY, TYPE_ENUM,
                List.of("feishu", "feishu_bot", "wecom"),
                "feishu=飞书应用私聊；feishu_bot=飞书群机器人；wecom=企业微信群机器人",
                "feishu"));
        schema.add(new ConfigDef(NOTIFY_FEISHU_APP_ID, "飞书 App ID", GROUP_NOTIFY, TYPE_TEXT,
                null, "开放平台应用 app_id（自有环境自填）", ""));
        schema.add(new ConfigDef(NOTIFY_FEISHU_APP_SECRET, "飞书 App Secret", GROUP_NOTIFY, TYPE_SECRET,
                null, "开放平台应用 app_secret", ""));
        schema.add(new ConfigDef(NOTIFY_FEISHU_USER_ID, "飞书接收人 open_id", GROUP_NOTIFY, TYPE_TEXT,
                null, "私聊接收人，一般以 ou_ 开头", ""));
        schema.add(new ConfigDef(NOTIFY_WEBHOOK_URL, "Webhook 地址", GROUP_NOTIFY, TYPE_SECRET,
                null, "仅 feishu_bot / wecom 需要：群机器人 webhook 完整 URL", ""));
        schema.add(new ConfigDef(NOTIFY_WEBHOOK_SECRET, "Webhook 签名密钥", GROUP_NOTIFY, TYPE_SECRET,
                null, "飞书群机器人「签名校验」密钥；企微一般留空", ""));
        schema.add(new ConfigDef(NOTIFY_WECHAT_TOKEN, "微信机器人 Token", GROUP_NOTIFY, TYPE_SECRET,
                null, "微信机器人拉「今日」等机机接口鉴权；与控制台密码可相同", ""));

        for (SysConfig row : mapper.selectList(null)) {
            cache.put(row.getConfigKey(), row.getConfigValue());
        }
        for (ConfigDef def : schema) {
            if (!cache.containsKey(def.key())) {
                SysConfig c = new SysConfig();
                c.setConfigKey(def.key());
                c.setConfigValue(def.defaultValue());
                c.setDescription(def.description());
                c.setUpdatedAt(LocalDateTime.now());
                mapper.insert(c);
                cache.put(def.key(), def.defaultValue());
            }
        }
        log.info("系统配置已加载，共 {} 项", cache.size());
    }

    private void putConfig(String key, String value) {
        ConfigDef def = findDef(key);
        SysConfig c = new SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setDescription(def != null ? def.description() : null);
        c.setUpdatedAt(LocalDateTime.now());
        if (mapper.updateById(c) == 0) {
            mapper.insert(c);
        }
        cache.put(key, value);
    }

    /** 取环境/yml 中该属性作为播种默认值；缺失时用 fallback。 */
    private String seed(String propKey, String fallback) {
        String v = env.getProperty(propKey);
        return v != null ? v : fallback;
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String getOrDefault(String key, String def) {
        String v = cache.get(key);
        return v != null ? v : def;
    }

    public boolean getBool(String key, boolean def) {
        String v = cache.get(key);
        return v != null ? "true".equalsIgnoreCase(v) : def;
    }

    public int getInt(String key, int def) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public List<SysConfigItemVO> items() {
        List<SysConfigItemVO> list = new ArrayList<>();
        for (ConfigDef def : schema) {
            list.add(new SysConfigItemVO(def.key(), cache.getOrDefault(def.key(), def.defaultValue()),
                    def.label(), def.group(), def.type(), def.options(), def.description()));
        }
        return list;
    }

    /** 前台公开配置（迅雷 SDK、公告等）。 */
    public SitePublicConfigVO publicSiteConfig() {
        SitePublicConfigVO vo = new SitePublicConfigVO();
        vo.setXunleiSdkEnabled(getBool(XUNLEI_SDK_ENABLED, false));
        vo.setXunleiPartnerCustom(getOrDefault(XUNLEI_PARTNER_CUSTOM, ""));
        vo.setEnabledPans(enabledPanLabels());
        vo.setNoticeEnabled(getBool(SITE_NOTICE_ENABLED, false));
        vo.setNoticeTitle(getOrDefault(SITE_NOTICE_TITLE, "网站公告"));
        vo.setNoticeContent(getOrDefault(SITE_NOTICE_CONTENT, ""));
        vo.setNoticeShowOnce(getBool(SITE_NOTICE_SHOW_ONCE, false));
        return vo;
    }

    /** 当前允许前台展示的网盘 label（按 {@link #PAN_DISPLAY_ORDER}）。 */
    public List<String> enabledPanLabels() {
        List<String> out = new ArrayList<>();
        for (String[] pan : orderedPanDisplay()) {
            if (getBool(PAN_DISPLAY_PREFIX + pan[1], true)) {
                out.add(pan[0]);
            }
        }
        return out;
    }

    /** 后台 pan.display.* 是否全部开启（含「其他」）。 */
    public boolean isAllPansEnabled() {
        for (String[] pan : PAN_DISPLAY) {
            if (!getBool(PAN_DISPLAY_PREFIX + pan[1], true)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 当前允许采集/入库的网盘类型（与 media_link.pan_type 一致）。
     * 配置 slug {@code pan115}/{@code pan123} 会映射为 {@code 115}/{@code 123}。
     * 「其他」开启时，在其顺序位置展开为 pikpak/ed2k。
     */
    public Set<String> enabledPanTypes() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String[] pan : PAN_DISPLAY) {
            flags.put(pan[1], getBool(PAN_DISPLAY_PREFIX + pan[1], true));
        }
        return enabledPanTypesFromFlags(flags);
    }

    /** 网盘选项（label + slug），后台域名开关与全局 pan.display 共用。 */
    public List<String[]> panDisplayOptions() {
        return List.copyOf(PAN_DISPLAY);
    }

    /** 按 slug 开关集合生成前台 label 列表（尊重页签顺序）。 */
    public List<String> enabledPanLabelsFromFlags(Map<String, Boolean> flags) {
        List<String> out = new ArrayList<>();
        if (flags == null) {
            return out;
        }
        for (String[] pan : orderedPanDisplay()) {
            if (Boolean.TRUE.equals(flags.get(pan[1]))) {
                out.add(pan[0]);
            }
        }
        return out;
    }

    /** 按 slug 开关集合生成 pan_type 集合（含 other→pikpak/ed2k）。 */
    public Set<String> enabledPanTypesFromFlags(Map<String, Boolean> flags) {
        Set<String> out = new LinkedHashSet<>();
        if (flags == null) {
            return out;
        }
        for (String[] pan : orderedPanDisplay()) {
            if ("other".equals(pan[1])) {
                if (Boolean.TRUE.equals(flags.get("other"))) {
                    out.addAll(OTHER_PAN_TYPES);
                }
                continue;
            }
            if (Boolean.TRUE.equals(flags.get(pan[1]))) {
                out.add(slugToPanType(pan[1]));
            }
        }
        return out;
    }

    /**
     * 按 {@code pan.display.order} 排列的网盘定义；未知 slug 忽略，缺项补到末尾。
     */
    public List<String[]> orderedPanDisplay() {
        Map<String, String[]> bySlug = new LinkedHashMap<>();
        for (String[] pan : PAN_DISPLAY) {
            bySlug.put(pan[1], pan);
        }
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String raw = getOrDefault(PAN_DISPLAY_ORDER, DEFAULT_PAN_ORDER);
        for (String part : raw.split(",")) {
            String slug = part.trim();
            if (slug.isEmpty() || seen.contains(slug)) {
                continue;
            }
            String[] row = bySlug.get(slug);
            if (row != null) {
                out.add(row);
                seen.add(slug);
            }
        }
        for (String[] pan : PAN_DISPLAY) {
            if (!seen.contains(pan[1])) {
                out.add(pan);
            }
        }
        return out;
    }

    /** 「其他」页签是否开启（pikpak/ed2k 及未单独列出的类型）。 */
    public boolean isOtherPanEnabled() {
        return getBool(PAN_DISPLAY_PREFIX + "other", true);
    }

    /** 该 pan_type 是否允许（与前台展示开关一致）。 */
    public boolean isPanTypeAllowed(String panType) {
        if (panType == null || panType.isBlank()) {
            return false;
        }
        String pan = panType.trim().toLowerCase();
        for (String[] row : PAN_DISPLAY) {
            if ("other".equals(row[1])) {
                continue;
            }
            if (pan.equals(slugToPanType(row[1]))) {
                return getBool(PAN_DISPLAY_PREFIX + row[1], true);
            }
        }
        if (OTHER_PAN_TYPES.contains(pan)) {
            return isOtherPanEnabled();
        }
        return isOtherPanEnabled();
    }

    /** 配置 slug → 入库/搜索使用的 pan_type。 */
    public static String slugToPanType(String slug) {
        if (slug == null) {
            return "";
        }
        return switch (slug) {
            case "pan115" -> "115";
            case "pan123" -> "123";
            default -> slug;
        };
    }

    @Transactional
    public List<SysConfigItemVO> updateMany(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new BizException("没有要更新的配置");
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            ConfigDef def = findDef(e.getKey());
            if (def == null) {
                throw new BizException("未知配置项：" + e.getKey());
            }
            String val = validate(def, e.getValue());
            SysConfig c = new SysConfig();
            c.setConfigKey(def.key());
            c.setConfigValue(val);
            c.setDescription(def.description());
            c.setUpdatedAt(LocalDateTime.now());
            if (mapper.updateById(c) == 0) {
                mapper.insert(c);
            }
            cache.put(def.key(), val);
        }
        return items();
    }

    private ConfigDef findDef(String key) {
        return schema.stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
    }

    private String validate(ConfigDef def, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (PAN_DISPLAY_ORDER.equals(def.key())) {
            return validatePanOrder(v);
        }
        switch (def.type()) {
            case TYPE_BOOL -> {
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw new BizException(def.label() + " 应为 true/false");
                }
                v = v.toLowerCase();
            }
            case TYPE_NUMBER -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new BizException(def.label() + " 应为非负整数");
                }
                if (n < 0 || n > 600000) {
                    throw new BizException(def.label() + " 应在 0～600000 之间");
                }
                v = String.valueOf(n);
            }
            case TYPE_ENUM -> {
                if (def.options() == null || !def.options().contains(v)) {
                    throw new BizException(def.label() + " 取值无效");
                }
            }
            case TYPE_TEXTAREA -> {
                if (v.length() > 50_000) {
                    throw new BizException(def.label() + " 过长（最多 50000 字）");
                }
            }
            default -> { /* TEXT/SECRET 不校验 */ }
        }
        return v;
    }

    /** 校验并规范化顺序：只保留已知 slug，去重，缺项补全。 */
    private String validatePanOrder(String raw) {
        Set<String> known = PAN_DISPLAY.stream().map(p -> p[1]).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String slug = part.trim();
                if (known.contains(slug)) {
                    ordered.add(slug);
                }
            }
        }
        for (String slug : known) {
            ordered.add(slug);
        }
        if (ordered.isEmpty()) {
            return DEFAULT_PAN_ORDER;
        }
        return String.join(",", ordered);
    }

    private record ConfigDef(String key, String label, String group, String type,
                             List<String> options, String description, String defaultValue) {
    }
}
