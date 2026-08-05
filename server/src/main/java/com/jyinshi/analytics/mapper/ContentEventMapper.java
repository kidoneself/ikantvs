package com.jyinshi.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.analytics.entity.ContentEvent;
import org.apache.ibatis.annotations.Mapper;

/** 行为事件数据访问。 */
@Mapper
public interface ContentEventMapper extends BaseMapper<ContentEvent> {
}
