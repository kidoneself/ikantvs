package com.jyinshi.transfer.pan.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jyinshi.transfer.pan.driver.PanType;
import lombok.Data;

/**
 * 一个网盘账号（本机持有，绑定本机出口 IP，永不跨 worker 流动 → 不异地）。
 *
 * <p>凭据按网盘不同：夸克/百度用 {@link #cookie}，迅雷用 openapi 的 {@link #refreshToken}。</p>
 */
@Data
public class Account {

    /** 网盘类型：quark/baidu/xunlei。 */
    private PanType type;

    /** 账号名（日志/记账用，别填敏感信息）。 */
    private String name;

    /**
     * 账号分工：transfer=用户转存号 / monitor=每日更新追更专用号（默认 transfer）。
     * 用户临时转存只在 transfer 号里选，日更号仅通过精确指定号名（追更）使用 → 各司其职。
     */
    private String role = "transfer";

    /** 权重，越大越常被选中（默认 1）。 */
    private int weight = 1;

    /** 是否启用。 */
    private boolean enabled = true;

    /** cookie（夸克/百度）。 */
    private String cookie;

    /** 迅雷 openapi refreshToken（迅雷用）。 */
    private String refreshToken;

    /** 百度开放平台 access_token（隐式授权，专供删除走 xpan 官方接口，避开网页删除验证码）。 */
    private String baiduAccessToken;

    /** 转存目标目录 fid/path（可空，driver 用默认根目录）。 */
    private String targetDirFid;

    // ---- 运行期健康状态（非配置项，不持久化）----

    /** 是否被标记为不健康（cookie/token 失效），暂时跳过。 */
    @JsonIgnore
    private volatile boolean unhealthy = false;

    /** 上次使用时间戳，用于最久未用优先的轮询。 */
    @JsonIgnore
    private volatile long lastUsedAt = 0L;

    // ---- 运行期账号信息（非配置项，不持久化；心跳上报给主站展示）----

    /** 昵称。 */
    @JsonIgnore
    private volatile String nickname;

    /** 网盘侧用户 id。 */
    @JsonIgnore
    private volatile String uid;

    /** 总空间（字节）；-1 未知。 */
    @JsonIgnore
    private volatile long totalSpace = -1;

    /** 已用空间（字节）；-1 未知。 */
    @JsonIgnore
    private volatile long usedSpace = -1;

    /** 上次拉取账号信息的时间戳（0=从未）。 */
    @JsonIgnore
    private volatile long infoAt = 0L;

    public boolean available() {
        return enabled && !unhealthy;
    }

    public void touch() {
        this.lastUsedAt = System.currentTimeMillis();
    }
}
