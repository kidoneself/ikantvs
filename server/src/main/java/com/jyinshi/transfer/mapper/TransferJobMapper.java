package com.jyinshi.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.transfer.entity.TransferJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TransferJobMapper extends BaseMapper<TransferJob> {

    /**
     * 领取一条可执行任务：pending + 到期可领 + 网盘类型在 worker 能力范围内。
     * 用 {@code FOR UPDATE SKIP LOCKED} 保证多 worker 并发领取不冲突（须在事务内调用）。
     *
     * @param panTypesCsv worker 支持的网盘类型，逗号分隔（如 "quark,baidu"）
     */
    @Select("SELECT * FROM transfer_job " +
            "WHERE status = 'pending' AND available_at <= #{now} " +
            "AND FIND_IN_SET(pan_type, #{panTypesCsv}) " +
            "ORDER BY priority DESC, id ASC LIMIT 1 " +
            "FOR UPDATE SKIP LOCKED")
    TransferJob selectClaimable(@Param("panTypesCsv") String panTypesCsv,
                                @Param("now") LocalDateTime now);

    /**
     * 回收租约超时的 running 任务：超过 max_attempts 直接置 failed，否则退回 pending 等待重派。
     */
    @Update("UPDATE transfer_job SET " +
            "status = CASE WHEN attempts >= max_attempts THEN 'failed' ELSE 'pending' END, " +
            "worker_id = NULL, lease_until = NULL, available_at = #{now}, " +
            "error_msg = CASE WHEN attempts >= max_attempts THEN '租约超时且已达最大重试' ELSE error_msg END " +
            "WHERE status = 'running' AND lease_until < #{now}")
    int requeueExpired(@Param("now") LocalDateTime now);

    /** 某条 media_link 是否已有同类型未完成任务（防重复入队）。 */
    @Select("SELECT COUNT(*) FROM transfer_job " +
            "WHERE media_link_id = #{mediaLinkId} AND job_type = #{jobType} " +
            "AND status IN ('pending','running')")
    int countActive(@Param("mediaLinkId") Long mediaLinkId, @Param("jobType") String jobType);

    /** 按分享链找一条未完成的同类型任务（用户转存无 mediaLinkId，用 share_url 去重）。 */
    @Select("SELECT * FROM transfer_job " +
            "WHERE share_url = #{shareUrl} AND job_type = #{jobType} " +
            "AND status IN ('pending','running') ORDER BY id DESC LIMIT 1")
    TransferJob findActiveByShareUrl(@Param("shareUrl") String shareUrl, @Param("jobType") String jobType);
}
