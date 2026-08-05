package com.jyinshi.analytics.mapper;

import com.jyinshi.analytics.dto.KeywordStat;
import com.jyinshi.analytics.dto.MediaHeat;
import com.jyinshi.analytics.dto.MediaStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 行为事件聚合查询（analytics 域报表用）。 */
@Mapper
public interface AnalyticsStatMapper {

    /** 热搜词：按搜索次数倒序。 */
    @Select("SELECT keyword AS keyword, COUNT(*) AS cnt FROM content_event "
            + "WHERE event_type='search' AND keyword IS NOT NULL AND keyword <> '' "
            + "AND created_at >= #{since} AND created_at < #{until} "
            + "GROUP BY keyword ORDER BY cnt DESC LIMIT #{limit}")
    List<KeywordStat> topSearches(@Param("since") LocalDateTime since,
                                  @Param("until") LocalDateTime until,
                                  @Param("limit") int limit);

    /** 求片榜：搜了但 0 结果的词，按频次倒序。 */
    @Select("SELECT keyword AS keyword, COUNT(*) AS cnt FROM content_event "
            + "WHERE event_type='search' AND num = 0 AND keyword IS NOT NULL AND keyword <> '' "
            + "AND created_at >= #{since} AND created_at < #{until} "
            + "GROUP BY keyword ORDER BY cnt DESC LIMIT #{limit}")
    List<KeywordStat> zeroResultSearches(@Param("since") LocalDateTime since,
                                         @Param("until") LocalDateTime until,
                                         @Param("limit") int limit);

    /** 某类型事件的媒体维度 Top（card_click / link_click）。 */
    @Select("SELECT media_id AS mediaId, COUNT(*) AS cnt FROM content_event "
            + "WHERE event_type=#{type} AND media_id IS NOT NULL "
            + "AND created_at >= #{since} AND created_at < #{until} "
            + "GROUP BY media_id ORDER BY cnt DESC LIMIT #{limit}")
    List<MediaStat> topMedia(@Param("type") String type,
                             @Param("since") LocalDateTime since,
                             @Param("until") LocalDateTime until,
                             @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM content_event WHERE event_type=#{type} "
            + "AND created_at >= #{since} AND created_at < #{until}")
    long countByType(@Param("type") String type,
                     @Param("since") LocalDateTime since,
                     @Param("until") LocalDateTime until);

    /** 独立访客数：按 visitor_id 去重（匿名/旧数据 visitor_id 为空的不计）。 */
    @Select("SELECT COUNT(DISTINCT visitor_id) FROM content_event "
            + "WHERE visitor_id IS NOT NULL "
            + "AND created_at >= #{since} AND created_at < #{until}")
    long countVisitors(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    /**
     * 近 N 天各片行为热度：link_click 权重 3，card_click 权重 1，按天数指数衰减（0.9^ageDays）。
     * 供热度回写 job 叠加到 media.hot_seed 上。无详情页后不再计入历史 view。
     */
    @Select("SELECT media_id AS mediaId, "
            + "ROUND(SUM(CASE event_type WHEN 'link_click' THEN 3 ELSE 1 END "
            + "  * POW(0.9, DATEDIFF(CURDATE(), DATE(created_at))))) AS score "
            + "FROM content_event "
            + "WHERE media_id IS NOT NULL AND event_type IN ('card_click','link_click') "
            + "AND created_at >= #{since} "
            + "GROUP BY media_id HAVING score > 0")
    List<MediaHeat> recentHeat(@Param("since") LocalDateTime since);
}
