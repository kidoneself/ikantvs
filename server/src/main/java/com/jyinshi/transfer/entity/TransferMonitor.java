package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 追更监控状态（transfer 域自持）。一条 media_link 至多一条监控。
 */
@Data
@TableName("transfer_monitor")
public class TransferMonitor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaLinkId;
    private String panType;
    /** 追更固定用的账号名（首转成功后回填，sync 用回同一个号）。 */
    private String accountName;
    private String shareUrl;
    private String sharePwd;

    private Boolean enabled;
    /** active/invalid/paused。 */
    private String status;

    /** 首转落地的固定夹 id（追更复用）。 */
    private String targetFolderId;
    /** 我方分享链（首转生成，追更不变）。 */
    private String myShareUrl;

    /** 源分享上次记录的更新时间戳（ms），追更核心。 */
    private Long lastUpdatedAt;
    private Integer lastFileCount;
    private String lastTitle;
    /** 最新集数/文件（展示用）。 */
    private String latestEpisode;

    private LocalDateTime lastProbeAt;
    /** 最近一次补到新集数的时间（真正更新，区别于 lastProbeAt 每次巡检都变的检查时间）。 */
    private LocalDateTime lastContentAt;
    private Integer failCount;

    /** 每剧检查日：0-6 对应周日-周六，逗号分隔；空=每天。为空则沿用全局巡检。 */
    private String checkDays;
    /** 每剧检查时段：如 "18-23"（止不含）；空=用全局巡检时段。 */
    private String checkHours;
    /** 每剧检查间隔（分钟）；空=用全局巡检间隔。 */
    private Integer checkInterval;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
