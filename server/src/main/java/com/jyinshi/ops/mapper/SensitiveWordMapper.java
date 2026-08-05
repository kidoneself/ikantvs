package com.jyinshi.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.ops.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/** 敏感词数据访问。 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
