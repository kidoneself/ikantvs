package com.jyinshi.transfer.pan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 进程内网盘执行器配置（前缀 {@code jyinshi.transfer.pan}）。
 *
 * <p>worker 合并进主站后，网盘账号与凭据集中存 {@code transfer_account} 表；此处只留运行期
 * 行为参数：执行并行度、账号探活间隔、内嵌节点名（单机单出口，固定一个 workerId）。
 * 迅雷应用级 key 走 {@code sys_config}（后台可配），不在此。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.transfer.pan")
public class PanWorkerProperties {

    /** 内嵌节点名：单机合并后所有账号归属这一个逻辑 worker。 */
    private String workerId = "local";

    /** 任务并行执行度：同时跑几个转存/追更/删除任务（默认 3，别贪多防风控）。 */
    private int execParallelism = 3;

    /** 账号凭据探活间隔（毫秒，默认 30 分钟）。<=0 关闭探活。 */
    private long healthCheckIntervalMs = 1_800_000L;

    /** 探活启动延迟（毫秒，默认 2 分钟）。 */
    private long healthCheckInitialDelayMs = 120_000L;
}
