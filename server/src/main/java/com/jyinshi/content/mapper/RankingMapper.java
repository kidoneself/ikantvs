package com.jyinshi.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.content.entity.Ranking;
import org.apache.ibatis.annotations.Mapper;

/** 榜单数据访问。 */
@Mapper
public interface RankingMapper extends BaseMapper<Ranking> {
}
