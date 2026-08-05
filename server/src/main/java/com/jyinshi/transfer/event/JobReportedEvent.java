package com.jyinshi.transfer.event;

import com.jyinshi.transfer.entity.TransferJob;

/**
 * 任务被 worker 回报后的领域事件。追更闭环（TransferMonitorService）监听它，
 * 与 TransferJobService 解耦（避免相互依赖）。
 *
 * @param job     回报后的任务（状态已更新）
 * @param success 是否成功
 */
public record JobReportedEvent(TransferJob job, boolean success) {
}
