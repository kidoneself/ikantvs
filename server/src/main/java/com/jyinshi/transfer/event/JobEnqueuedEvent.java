package com.jyinshi.transfer.event;

/**
 * 任务入队后的领域事件。{@code PanJobRunner} 监听它「入队即唤醒」：合并进单进程后无需再靠定时轮询
 * 发现新任务，enqueue 提交后立刻触发一次领取执行，去掉轮询延迟。定时轮询仅保留为安全网
 * （退避重试、租约回收后回到 pending 的任务）。
 *
 * @param panType 入队任务的网盘类型（小写），仅供日志/判断本机是否支持
 */
public record JobEnqueuedEvent(String panType) {
}
