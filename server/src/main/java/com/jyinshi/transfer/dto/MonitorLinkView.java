package com.jyinshi.transfer.dto;

import com.jyinshi.transfer.entity.TransferMonitor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 追更链运行态视图（transfer 域对外只读投影）。
 *
 * <p>跨域用：content 域「每日更新」按 mediaLinkId 拿一条追更链的展示信息（源链/我方链/最新/状态），
 * 不直接读 transfer 的表或实体。</p>
 */
@Data
public class MonitorLinkView {

    private Long mediaLinkId;
    private String panType;
    /** 上游源分享链（被追更的源）。 */
    private String shareUrl;
    /** 指定的监控账号名。 */
    private String accountName;
    /** active/invalid/paused。 */
    private String status;
    /** 我方稳定分享链（首转生成，前台展示这个）。 */
    private String myShareUrl;
    /** 最新集数/文件名（原始，展示前可再做智能提取）。 */
    private String latestEpisode;
    /** 源分享标题（常含「更新至N集」，文件名乱时作兜底）。 */
    private String lastTitle;
    private LocalDateTime updatedAt;
    /** 上次检查（巡检）时间。 */
    private LocalDateTime lastCheckAt;
    /** 上次真正补到新集数的时间。 */
    private LocalDateTime lastContentAt;
    /** 每剧追更节奏（空=沿用全局）。 */
    private String checkDays;
    private String checkHours;
    private Integer checkInterval;
    /** 是否仍在巡检池中（完结/暂停后为 false）。 */
    private Boolean enabled;

    public static MonitorLinkView of(TransferMonitor m) {
        MonitorLinkView v = new MonitorLinkView();
        v.setMediaLinkId(m.getMediaLinkId());
        v.setPanType(m.getPanType());
        v.setShareUrl(m.getShareUrl());
        v.setAccountName(m.getAccountName());
        v.setStatus(m.getStatus());
        v.setMyShareUrl(m.getMyShareUrl());
        v.setLatestEpisode(m.getLatestEpisode());
        v.setLastTitle(m.getLastTitle());
        v.setUpdatedAt(m.getUpdatedAt());
        v.setLastCheckAt(m.getLastProbeAt());
        v.setLastContentAt(m.getLastContentAt());
        v.setCheckDays(m.getCheckDays());
        v.setCheckHours(m.getCheckHours());
        v.setCheckInterval(m.getCheckInterval());
        v.setEnabled(m.getEnabled());
        return v;
    }
}
