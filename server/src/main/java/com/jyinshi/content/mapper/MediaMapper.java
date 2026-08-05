package com.jyinshi.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.content.entity.Media;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 媒体信息数据访问。仅做数据存取，业务逻辑放在 service。
 */
@Mapper
public interface MediaMapper extends BaseMapper<Media> {

    /** 热度回写：把所有片的 hot 归位到基线 hot_seed（无近期行为的片随之衰减回种子）。 */
    @Update("UPDATE media SET hot = hot_seed WHERE hot <> hot_seed")
    int resetHotToSeed();

    /** 热度回写：对有近期行为的片，hot = hot_seed + 行为分。 */
    @Update("UPDATE media SET hot = hot_seed + #{score} WHERE id = #{id}")
    int applyHeat(@Param("id") Long id, @Param("score") int score);
}
