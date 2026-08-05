package com.jyinshi.content.ingest;

import lombok.Data;

/** 一次采集入库的结果（日志 / 后台保鲜）。 */
@Data
public class IngestResult {

    /** done=采集完成 / cooldown=冷却内跳过 / disabled=开关关闭 / no_source=无启用来源。 */
    private String status;
    /** 本次新增链接数。 */
    private int added;
    /** 本次刷新的已有链接数（note/新鲜度更新）。 */
    private int updated;
    /** 命中门槛被丢弃数（广告/敏感/不相关）。 */
    private int skipped;

    public static IngestResult status(String status) {
        IngestResult r = new IngestResult();
        r.status = status;
        return r;
    }

    public boolean hasNew() {
        return added > 0;
    }
}
