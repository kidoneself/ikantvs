package com.jyinshi.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.search.entity.DocMonitorTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocMonitorTaskMapper extends BaseMapper<DocMonitorTask> {

    @Select("SELECT * FROM doc_monitor_task WHERE status = 1 ORDER BY source, id")
    List<DocMonitorTask> selectEnabled();
}
