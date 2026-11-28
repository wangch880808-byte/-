package com.ruoyi.education.overview.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ruoyi.education.overview.port.AssignmentTodoPort;
import com.ruoyi.education.overview.port.CourseScopePort;
import com.ruoyi.education.overview.port.GradeTodoPort;
import com.ruoyi.education.overview.port.NoticeSummaryPort;
import com.ruoyi.education.overview.port.SessionStatusPort;
import com.ruoyi.education.overview.service.OverviewQueryService;
import com.ruoyi.education.overview.support.OverviewSourceGuard;
import com.ruoyi.education.overview.support.OverviewWindowFactory;

/**
 * 总览聚合的独立装配。
 *
 * <p>源线程池与聚合线程池分开：聚合线程等待源结果时不会占满同一线程池，
 * 避免自等待死锁。两个池都有固定大小和守护线程；生产接入时应按数据源
 * 延迟与并发量调整，或改为可配置属性。</p>
 *
 * <p>TODO：当前超时、池大小写死为本地默认值，待第 2-5 组契约和真实延迟
 * 联调后外置为配置项；解除条件为真实适配器接入并完成压测。</p>
 */
@Configuration
public class OverviewConfiguration
{
    private static final int POOL_SIZE = 4;

    private static final long SOURCE_TIMEOUT_MS = 1500L;

    private static final long OVERALL_TIMEOUT_MS = 2500L;

    /**
     * 总览服务端时钟。
     *
     * @return UTC 时钟
     */
    @Bean(name = "overviewClock")
    public Clock overviewClock()
    {
        return Clock.systemUTC();
    }

    /**
     * 数据源线程池。
     *
     * @return 固定大小、守护线程的源执行器
     */
    @Bean(name = "overviewSourceExecutor", destroyMethod = "shutdown")
    public ExecutorService overviewSourceExecutor()
    {
        return newExecutor("overview-source");
    }

    /**
     * 聚合线程池。
     *
     * @return 固定大小、守护线程的聚合执行器
     */
    @Bean(name = "overviewAggregationExecutor", destroyMethod = "shutdown")
    public ExecutorService overviewAggregationExecutor()
    {
        return newExecutor("overview-aggregation");
    }

    /**
     * 数据源守卫。
     *
     * @param executor 源执行器
     * @return 单源超时守卫
     */
    @Bean
    public OverviewSourceGuard overviewSourceGuard(
            @Qualifier("overviewSourceExecutor") Executor executor)
    {
        return new OverviewSourceGuard(executor, Duration.ofMillis(SOURCE_TIMEOUT_MS));
    }

    /**
     * 时间窗工厂。
     *
     * @param clock 服务端时钟
     * @return 时间窗工厂
     */
    @Bean
    public OverviewWindowFactory overviewWindowFactory(
            @Qualifier("overviewClock") Clock clock)
    {
        return new OverviewWindowFactory(clock);
    }

    /**
     * 总览聚合服务。
     *
     * <p>端口使用 {@link ObjectProvider} 可选注入：当前生产适配器未接入时为
     * null，guard 会把对应 item 映射为 UNAVAILABLE，不返回假数据。</p>
     *
     * @param courseScopePort     课程范围端口提供者
     * @param sessionStatusPort   课堂状态端口提供者
     * @param assignmentTodoPort  任务待办端口提供者
     * @param gradeTodoPort       成绩待办端口提供者
     * @param noticeSummaryPort   通知汇总端口提供者
     * @param sourceGuard         数据源守卫
     * @param aggregationExecutor 聚合执行器
     * @param clock               服务端时钟
     * @return 聚合服务
     */
    @Bean
    public OverviewQueryService overviewQueryService(
            ObjectProvider<CourseScopePort> courseScopePort,
            ObjectProvider<SessionStatusPort> sessionStatusPort,
            ObjectProvider<AssignmentTodoPort> assignmentTodoPort,
            ObjectProvider<GradeTodoPort> gradeTodoPort,
            ObjectProvider<NoticeSummaryPort> noticeSummaryPort,
            OverviewSourceGuard sourceGuard,
            @Qualifier("overviewAggregationExecutor") Executor aggregationExecutor,
            @Qualifier("overviewClock") Clock clock)
    {
        return new OverviewQueryService(
                courseScopePort.getIfAvailable(),
                sessionStatusPort.getIfAvailable(),
                assignmentTodoPort.getIfAvailable(),
                gradeTodoPort.getIfAvailable(),
                noticeSummaryPort.getIfAvailable(),
                sourceGuard,
                aggregationExecutor,
                Duration.ofMillis(OVERALL_TIMEOUT_MS),
                clock);
    }

    private ExecutorService newExecutor(String prefix)
    {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(POOL_SIZE, runnable ->
        {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
