package com.jyinshi.content.dto;

import lombok.Data;

/** 入池一条的结果 / 自营进度。 */
@Data
public class PoolIngestRowVO {

    private Long id;
    private String title;
    private String panType;
    private String panLabel;
    /** 上游源链。 */
    private String url;
    /** 我方永久分享链（自营 done 时）。 */
    private String shareUrl;
    /**
     * peer：added / updated / skipped / failed
     * self：transferring / done / failed / skipped
     */
    private String status;
    private String reason;

    public static PoolIngestRowVO of(String title, String panType, String panLabel,
                                     String url, String status, String reason) {
        PoolIngestRowVO r = new PoolIngestRowVO();
        r.title = title;
        r.panType = panType;
        r.panLabel = panLabel;
        r.url = url;
        r.status = status;
        r.reason = reason;
        return r;
    }
}
