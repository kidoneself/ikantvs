package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * worker 账号（方案A：凭据集中存主站）。元数据 + 凭据都在这里：cookie/refresh_token 存
 * {@link #credential}，worker 内存持有并从主站拉取，不再落 worker 磁盘。健康/昵称/空间等
 * 由 worker 心跳回报更新。
 */
@Data
@TableName("transfer_account")
public class TransferAccount implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workerId;
    private String panType;
    private String accountName;

    /**
     * 历史字段，不再用于选号。选号改走每盘「追更号 / 片库号」指针。
     * 存量 role=monitor 仅作追更号未配置时的迁移回退。
     */
    private String role;

    /** 凭据：cookie（夸克/百度）或 refresh_token（迅雷）。方案A集中存主站，worker 拉取入内存。 */
    private String credential;

    /**
     * 百度开放平台 access_token（隐式授权，专供删除走 xpan 官方接口，避开网页删除验证码）。
     * 与 {@link #credential}(cookie) 并存：cookie 做转存/巡检，token 做删除。约 30 天到期需重授权。
     */
    private String baiduAccessToken;
    /** 百度 access_token 到期时间（隐式授权不可刷新，据此提示后台重新授权）。 */
    private LocalDateTime baiduTokenExpireAt;

    /** 转存目标目录 fid/path（可空，driver 用默认根目录）。 */
    private String targetDirFid;

    /** 网盘昵称（worker 心跳上报）。 */
    private String nickname;
    /** 网盘侧用户 id。 */
    private String uid;
    /** 总空间（字节）；null/负 表示未知。 */
    private Long totalSpace;
    /** 已用空间（字节）；null/负 表示未知。 */
    private Long usedSpace;

    private Boolean enabled;
    /** 凭据是否有效（worker 标记，false=失效需重扫）。 */
    private Boolean healthy;
    /** 待移除：后台已请求删除，等 worker 心跳落地后据对账清行。 */
    private Boolean removing;

    private String note;
    private LocalDateTime lastSeenAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 是否已有凭据（派生字段，不入库）。后台列表抹掉 credential 前先置位，
     * 让前端能区分「未登录（无凭据）」与「已登录但失效」，不再拿 healthy 冒充有效。
     */
    @TableField(exist = false)
    private Boolean hasCredential;

    /** 是否已配置百度删除令牌（派生，不入库）。列表脱敏时置位，前端据此显示「已授权/未授权」。 */
    @TableField(exist = false)
    private Boolean hasBaiduToken;
    /** 百度删除令牌是否已过期（派生，不入库）。 */
    @TableField(exist = false)
    private Boolean baiduTokenExpired;
    /** 百度删除令牌剩余天数（派生，不入库，负=已过期，null=未配置/无到期）。 */
    @TableField(exist = false)
    private Long baiduTokenDaysLeft;
}
