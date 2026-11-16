package com.ruoyi.education.group3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 第三组异步处理与定时清理配置。
 *
 * <p>课件/文档转换为 CPU 密集型且可能耗时较长，使用独立有界线程池，避免占用若依共享线程池；
 * 队列满时由调用线程执行（{@code CallerRunsPolicy}），形成自然背压。保留策略的定时清理任务
 * 也在本配置开启调度后运行。</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class Group3AsyncConfig
{
    @Bean(name = "group3ConversionExecutor")
    public ThreadPoolTaskExecutor group3ConversionExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("group3-conv-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
