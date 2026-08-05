package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 每日更新（content 域 · 策展看板）。薄策展层：一条 = 一部剧 + 排序/置顶/上架开关。
 *
 * <p>链接不落这里：上游源链 + 追更状态挂 transfer 域（transfer_monitor），锚在 media_link(source='self')；
 * 我方稳定分享链、最新集数在查询时经 transfer 服务聚合出来。一部剧至多一条。</p>
 */
@Data
@TableName("daily_update")
public class DailyUpdate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    /** 1=置顶。 */
    private Integer pinned;
    /** 展示顺序，大在前。 */
    private Integer sort;
    /** 1=上架（前台可见）。 */
    private Integer enabled;
    /** 运营手动填写的最新集数/日期；展示时与自动聚合值取较新（只增不减、保护手动纠正）。 */
    private String manualEpisode;
    /** 1=已完结：停追更巡检，保留我方链；换号/号满时不迁。剧级一次，不按盘拆。 */
    private Integer ended;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
