package com.jyinshi.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.search.entity.DocMonitorHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocMonitorHistoryMapper extends BaseMapper<DocMonitorHistory> {

    @Delete("DELETE FROM doc_monitor_history WHERE task_id = #{taskId}")
    int deleteByTaskId(Long taskId);
}
