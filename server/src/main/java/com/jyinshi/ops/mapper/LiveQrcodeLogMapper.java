package com.jyinshi.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.ops.entity.LiveQrcodeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LiveQrcodeLogMapper extends BaseMapper<LiveQrcodeLog> {

    @Select("SELECT IFNULL(NULLIF(source, ''), '直接访问') AS source, COUNT(*) AS count "
            + "FROM live_qrcode_log GROUP BY source ORDER BY count DESC")
    List<Map<String, Object>> countBySource();

    @Select("SELECT COUNT(*) FROM live_qrcode_log WHERE DATE(created_at) = CURDATE()")
    int countToday();

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count "
            + "FROM live_qrcode_log "
            + "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> countByDateRecent(@Param("days") int days);
}
