package com.jyinshi.content.dto;

import com.jyinshi.content.entity.MediaLink;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台链接搜索列表项。 */
@Data
public class MediaLinkAdminVO {

    private Long id;
    private Long mediaId;
    private String mediaTitle;
    private String panType;
    private String panLabel;
    private String url;
    private String note;
    private String source;
    private String status;
    private Integer invalid;
    private String checkState;
    private LocalDateTime updatedAt;

    public static MediaLinkAdminVO from(MediaLink row, String mediaTitle, String panLabel) {
        MediaLinkAdminVO vo = new MediaLinkAdminVO();
        vo.id = row.getId();
        vo.mediaId = row.getMediaId();
        vo.mediaTitle = mediaTitle;
        vo.panType = row.getPanType();
        vo.panLabel = panLabel;
        vo.url = row.getUrl();
        vo.note = row.getNote();
        vo.source = row.getSource();
        vo.status = row.getStatus();
        vo.invalid = row.getInvalid();
        vo.checkState = row.getCheckState();
        vo.updatedAt = row.getUpdatedAt();
        return vo;
    }
}
