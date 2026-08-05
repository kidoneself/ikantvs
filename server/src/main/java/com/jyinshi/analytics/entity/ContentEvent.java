package com.jyinshi.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 站内行为事件（analytics 域）：搜索 / 卡片点击 / 链点击等，只追加。 */
@Data
@TableName("content_event")
public class ContentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联媒体；搜索词事件可空。 */
    private Long mediaId;

    /** search / link_click / card_click（历史 view 仅存量） */
    private String eventType;

    /** 匿名访客标识（前端 localStorage UUID），用于去重与独立访客分析。 */
    private String visitorId;

    /** 搜索词（search 事件）。 */
    private String keyword;

    /** 附加标签，如网盘类型（link_click）。 */
    private String tag;

    /** 数值，如搜索结果数（search）。 */
    private Integer num;

    private LocalDateTime createdAt;
}
