package com.jyinshi.analytics.dto;

import lombok.Data;

/**
 * 带环比的指标：当前周期 current，上一同长周期 previous，
 * changePct = (current - previous) / previous × 100（previous=0 时为 null）。
 *
 * <p>字段不用 value：后台 Vue 模板里 .value 会跟 ref 解包撞车。
 */
@Data
public class MetricDelta {

    private long current;
    private long previous;
    /** 相对上期变化百分比；上期为 0 时无法计算，前端显示「新增」或「—」。 */
    private Double changePct;

    public static MetricDelta of(long current, long previous) {
        MetricDelta d = new MetricDelta();
        d.setCurrent(current);
        d.setPrevious(previous);
        if (previous > 0) {
            d.setChangePct(Math.round((current - previous) * 1000.0 / previous) / 10.0);
        }
        return d;
    }
}
