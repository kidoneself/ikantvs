package com.jyinshi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池。目前专供 analytics 埋点等旁路任务，队列满则丢弃（埋点丢一两条无所谓，
 * 绝不阻塞主请求）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("analytics-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * 资源采集入库线程池：给 SSE 搜索沉淀、发现入库、后台保鲜用。每个任务会打外部来源（pansou 等，
     * 数秒级），故池要有界；满了直接拒绝（AbortPolicy），调用方跳过，绝不阻塞主请求。
     */
    @Bean("ingestExecutor")
    public Executor ingestExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("ingest-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }

    /** 流式搜索编排线程池：每个用户搜索占一条，内再扇出多源。 */
    @Bean("searchExecutor")
    public Executor searchExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("search-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /** 单源搜索线程池：Gying/SeedHub/PanSou 并行。 */
    @Bean("sourceSearchExecutor")
    public Executor sourceSearchExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(12);
        ex.setMaxPoolSize(48);
        ex.setQueueCapacity(400);
        ex.setThreadNamePrefix("search-src-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * 重型定时任务卸载池：内容同步 / 文档监控等分钟～小时级工作丢到这里，
     * 调度线程只负责触发，避免占满 {@code scheduling-*} 池。
     */
    @Bean("scheduledWorkExecutor")
    public Executor scheduledWorkExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(16);
        ex.setThreadNamePrefix("sched-work-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.initialize();
        return ex;
    }
}
