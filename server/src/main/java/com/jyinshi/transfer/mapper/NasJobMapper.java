package com.jyinshi.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.transfer.entity.NasJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NasJobMapper extends BaseMapper<NasJob> {

    @Select("SELECT COUNT(*) FROM nas_job " +
            "WHERE media_link_id = #{mediaLinkId} AND status IN ('pending','running')")
    int countActiveByMediaLink(@Param("mediaLinkId") Long mediaLinkId);

    /** 只取消尚未被千云领走的 pending，running 不动（避免半传打断）。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE nas_job SET status='cancelled', error_msg=#{reason}, updated_at=NOW() " +
            "WHERE media_link_id = #{mediaLinkId} AND status = 'pending'")
    int cancelPendingByMediaLink(@Param("mediaLinkId") Long mediaLinkId,
                                 @Param("reason") String reason);
}
