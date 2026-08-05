package com.jyinshi.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.content.entity.RankingItem;
import org.apache.ibatis.annotations.Mapper;

/** 榜单条目数据访问。 */
@Mapper
public interface RankingItemMapper extends BaseMapper<RankingItem> {
}
