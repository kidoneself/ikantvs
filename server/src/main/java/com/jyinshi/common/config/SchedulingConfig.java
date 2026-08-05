package com.jyinshi.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 定时任务线程池。
 *
 * <p>Spring 默认 {@code @Scheduled} 是<strong>单线程</strong>：任一长任务（如 ingest 保鲜打外站）
 * 卡住，清理过期转存、追更巡检、租约回收等全部停摆。本配置改为固定多线程池，互不堵死。</p>
 */
@Slf4j
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    /** 覆盖常见并发：清理 + 巡检 + 保活 + poll + 偶发重任务触发，留余量。 */
    private static final int POOL_SIZE = 8;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setErrorHandler(t -> log.error("[scheduling] 定时任务未捕获异常", t));
        scheduler.initialize();
        return scheduler;
    }
}
