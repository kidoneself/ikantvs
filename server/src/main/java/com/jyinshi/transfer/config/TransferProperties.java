package com.jyinshi.transfer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * transfer 域配置（对应 {@code jyinshi.transfer.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.transfer")
public class TransferProperties {

    /** 领取任务后的租约时长（秒）；执行器须在此时间内回报，否则任务被回收重派（防进程崩溃卡单）。 */
    private int leaseSeconds = 120;

    /** 失败重试退避基数（秒）；实际退避 = backoffSeconds * attempts。 */
    private int retryBackoffSeconds = 60;

    /** 追更巡检调度。 */
    private Monitor monitor = new Monitor();

    /** 用户转存（点击转存）。 */
    private UserTransfer userTransfer = new UserTransfer();

    /** 自营录入（片库号永久转存）。 */
    private Library library = new Library();

    /** 百度开放平台（隐式授权拿 access_token，专供走 xpan 官方接口删除，避开网页删除的验证码）。 */
    private Baidu baidu = new Baidu();

    /**
     * 百度隐式授权配置。借用公开 app 的 client-id（无 secret，走 response_type=token），
     * 授权页 redirect_uri=oob 直接把 access_token 显示在页面，运营复制粘回后台按号保存。
     * access_token 约 30 天有效、不可刷新，到期重新授权即可。
     */
    @Data
    public static class Baidu {
        /** 借用的公开 app client-id（沿用老项目 qianyun：LinkSwift/OpenList 那套）。 */
        private String clientId = "omiOnr2tYnN9vSyDErcVFWpPU2mZA7YO";
        private String scope = "basic,netdisk";
    }

    /**
     * 用户转存配置：命中复用窗口 + 临时文件保留 + 清理。
     * 逻辑参照老站：转存到平台网盘生成我方分享链，缓存/复用一段时间，到期清理临时文件。
     */
    @Data
    public static class UserTransfer {

        /**
         * 转存结果保留时长（分钟）。记录 expire_time = 转存时间 + 本值；
         * 复用窗口 = 本值（窗口内点同一资源直接返回缓存我方链，不重复转）。
         */
        private int retentionMinutes = 30;

        /** 清理任务 cron（默认每 30 分钟，把过期非永久记录按网盘攒成一批一起删）。 */
        private String cleanupCron = "0 */30 * * * *";

        /** 单轮清理最多处理多少条过期记录（会按网盘分组，每组合并成一个 delete 任务）。 */
        private int cleanupBatchLimit = 200;

        /** 这些网盘的转存永久保留、不参与清理（迅雷临时文件无空间压力）。 */
        private List<String> permanentPanTypes = List.of("xunlei");

        /**
         * 用户转存的顶层落地目录名（各盘根目录下自动建/复用）。首转的「剧名」夹落进它，
         * 与追更资源物理隔离：这里的东西会被定时清理，手动清空也不会误伤追更。
         */
        private String landingDir = "临时转存";
    }

    /**
     * 追更巡检调度配置。probe 免登录不碰账号，可以放心密集；只有发现更新才入队 sync（动账号）。
     */
    @Data
    public static class Monitor {

        /** 总开关。 */
        private boolean enabled = true;

        /**
         * 活跃巡检时段（本地时，"起-止" 小时，止不含）。默认覆盖上午 + 中午 + 晚上更新习惯。
         * 段内每 {@link #windowIntervalMinutes} 分钟入队一轮 probe。
         */
        private List<String> windows = List.of("09-11", "11-14", "18-23");

        /** 活跃时段内的巡检间隔（分钟）。 */
        private int windowIntervalMinutes = 30;

        /** 整点全局补扫（无视间隔强制扫一遍，"想补一下"常态化）。 */
        private List<Integer> extraCheckHours = List.of(9, 12, 18, 22);

        /** 补扫的最小间隔（分钟），防止和常规巡检重复触发。 */
        private int extraCheckMinIntervalMinutes = 120;

        /** 单次 tick 最多入队多少条 probe（防一次打太多）。 */
        private int batchLimit = 200;

        /**
         * 追更的顶层落地目录名（各盘根目录下自动建/复用）。追更首转的「剧名」夹落进它，
         * 只增不减、永久保留，是你的"剧库"，与用户临时转存分开互不影响。
         */
        private String landingDir = "追更资源";
    }

    /**
     * 自营录入落地目录：永久保留，不参与临时转存清理，也不和每日更新的「追更资源」混用。
     */
    @Data
    public static class Library {
        private String landingDir = "自营片库";
    }
}
