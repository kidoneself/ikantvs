package com.jyinshi.transfer.pan.driver;

import lombok.Data;

/**
 * 分享信息（追更巡检 + 死活检测共用）。
 *
 * <p>追更靠 {@link #updatedAt} 时间戳对比；检测靠 {@link #checkState}。
 * 夸克/百度取此信息免登录，迅雷需账号态。</p>
 */
@Data
public class ShareInfo {

    /** 是否成功取到（网络/解析层面）。 */
    private boolean ok;

    /**
     * 检测结论：ok/bad/locked/unsupported/uncertain。
     * <p>只有明确 bad（分享取消/不存在）才代表死链；超时/异常一律 uncertain，主站不据此标失效。</p>
     */
    private String checkState;

    /** 分享标题（含"更新至N集"等信息，供主站更新 note）。 */
    private String title;

    /** 最后更新时间戳（毫秒）——追更核心，变大即有新内容。 */
    private Long updatedAt;

    /** 文件总数（辅助判断更新）。 */
    private Integer fileCount;

    /** 总大小（字节，辅助）。 */
    private Long size;

    /** 过期时间戳（毫秒，可空）。 */
    private Long expiredAt;

    /** 说明（成功/失败原因），便于排查。 */
    private String message;

    public static ShareInfo bad(String message) {
        ShareInfo i = new ShareInfo();
        i.ok = true;
        i.checkState = "bad";
        i.message = message;
        return i;
    }

    public static ShareInfo uncertain(String message) {
        ShareInfo i = new ShareInfo();
        i.ok = false;
        i.checkState = "uncertain";
        i.message = message;
        return i;
    }
}
