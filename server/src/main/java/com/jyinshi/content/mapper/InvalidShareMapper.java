package com.jyinshi.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.content.entity.InvalidShare;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvalidShareMapper extends BaseMapper<InvalidShare> {

    /**
     * 幂等写入黑名单：唯一键 {@code (pan_type, share_id)} 命中则忽略（不覆盖原始失效原因）。
     *
     * @return 1=新增，0=已存在
     */
    @Insert("""
            INSERT IGNORE INTO invalid_share
              (pan_type, share_id, error_code, reason, created_at, updated_at)
            VALUES
              (#{panType}, #{shareId}, #{errorCode}, #{reason}, NOW(), NOW())
            """)
    int insertIgnore(@Param("panType") String panType,
                     @Param("shareId") String shareId,
                     @Param("errorCode") String errorCode,
                     @Param("reason") String reason);
}
