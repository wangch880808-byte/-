package com.ruoyi.education.overview.support;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.education.overview.dto.OverviewStatus;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 聚合数据源统一调用与失败收敛。
 *
 * <p>把缺适配器、超时、异常、线程中断和执行器拒绝统一映射为
 * {@link OverviewStatus#UNAVAILABLE}；把 null 或空结果映射为
 * {@link OverviewStatus#NO_DATA}。调用方不需要重复写 try/catch，也不会在
 * 失败时误写成 0。</p>
 *
 * <p>设计原因：总览是多个来源的拼装，单个来源失败不能拖垮整个响应；
 * 但也不能把失败伪装成“没有数据”。因此失败项保留在响应中并标记
 * UNAVAILABLE，前端可以分别展示“暂无数据”和“暂不可用”。</p>
 *
 * <p>批量要求：本类只负责单次调用；端口方法本身必须批量查询，调用方应把
 * 同一来源的多个 ID 合并成一次调用，避免 N+1。</p>
 *
 * <p>线程模型：必须传入异步、隔离的 Executor，不能使用直接执行器。
 * 超时或中断时本类取消内部 {@link FutureTask}，{@code cancel(true)} 只会请求中断
 * 执行线程，无法保证不响应中断的阻塞 I/O 已停止。调用方对 CompletableFuture
 * 的取消不会自动传递到本类，也不能作为端口调用已停止的证据。生产接入时应为
 * 每个来源设置独立线程池或有界队列，避免一个慢源耗尽公共线程。</p>
 */
public class OverviewSourceGuard
{
    private static final Logger log = LoggerFactory.getLogger(OverviewSourceGuard.class);

    private final Executor executor;

    private final Duration timeout;

    /**
     * 创建数据源守卫。
     *
     * @param executor 异步执行器，不能为 null；不能是直接执行器
     * @param timeout  单次查询超时，必须大于 0
     * @throws OverviewValidationException 参数非法
     */
    public OverviewSourceGuard(Executor executor, Duration timeout)
    {
        if (executor == null)
        {
            throw new OverviewValidationException("executor 不能为空");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative())
        {
            throw new OverviewValidationException("timeout 必须大于 0");
        }
        this.executor = executor;
        this.timeout = timeout;
    }

    /**
     * 查询数据源，空集合/空 Map 默认映射 NO_DATA。
     *
     * @param source   来源领域，不能为空
     * @param supplier 数据源调用；为 null 表示适配器缺失
     * @param <T>      结果类型
     * @return 统一三态结果
     * @throws OverviewValidationException source 为空
     */
    public <T> OverviewSourceResult<T> query(String source, Supplier<T> supplier)
    {
        return query(source, supplier, null);
    }

    /**
     * 查询数据源，并按空值判定映射 NO_DATA。
     *
     * <p>映射规则：supplier 为 null（缺适配器）→ UNAVAILABLE；返回 null →
     * NO_DATA；emptyPredicate 非 null 时按其判定空值，为 null 时默认检测空
     * Collection/Map；抛出异常、超时、中断、执行器拒绝 → UNAVAILABLE；
     * 其余 → OK。失败原因只写日志。</p>
     *
     * @param source          来源领域，不能为空
     * @param supplier        数据源调用；为 null 表示适配器缺失
     * @param emptyPredicate  空结果判定，可为 null；返回 true 时映射 NO_DATA
     * @param <T>             结果类型
     * @return 统一三态结果
     * @throws OverviewValidationException source 为空
     */
    public <T> OverviewSourceResult<T> query(String source, Supplier<T> supplier,
            Predicate<T> emptyPredicate)
    {
        return query(source, supplier, emptyPredicate, null);
    }

    /**
     * 查询数据源，并把单次超时限制在请求剩余预算内。
     *
     * <p>调用方在提交任务前和本方法在真正调用 supplier 前都会读取
     * {@code remainingBudgetNanos}。任务排队期间预算耗尽时，supplier 不会被调用，
     * 结果映射为 UNAVAILABLE。实际等待时间取“单源超时”和“当前剩余预算”的较小值，
     * 避免排队后的调用重新获得一份完整单源超时。</p>
     *
     * <p>本方法取消的是内部 FutureTask；{@code cancel(true)} 只请求中断执行线程，
     * 不保证底层阻塞 I/O 停止。</p>
     *
     * @param source               来源领域，不能为空
     * @param supplier             数据源调用；为 null 表示适配器缺失
     * @param emptyPredicate       空结果判定，可为 null
     * @param remainingBudgetNanos 请求剩余预算的单调纳秒来源；null 表示不启用预算检查
     * @param <T>                  结果类型
     * @return 统一三态结果
     * @throws OverviewValidationException source 为空
     */
    public <T> OverviewSourceResult<T> query(String source, Supplier<T> supplier,
            Predicate<T> emptyPredicate, LongSupplier remainingBudgetNanos)
    {
        if (source == null || source.isBlank())
        {
            throw new OverviewValidationException("source 不能为空");
        }
        if (supplier == null)
        {
            // 缺适配器不是“没有数据”，不能降级成 0 或空集合。
            log.warn("总览数据源适配器缺失: source={}", source);
            return OverviewSourceResult.unavailable(source, "adapter missing");
        }
        if (remainingBudgetNanos != null && remainingBudgetNanos.getAsLong() <= 0L)
        {
            log.warn("总览数据源提交前预算已耗尽: source={}", source);
            return OverviewSourceResult.unavailable(source, "aggregation budget exhausted");
        }

        FutureTask<T> task = new FutureTask<>(() ->
        {
            // 任务可能先进入源线程池队列；真正调用端口前再检查一次预算。
            if (remainingBudgetNanos != null && remainingBudgetNanos.getAsLong() <= 0L)
            {
                throw new BudgetExhaustedException();
            }
            return supplier.get();
        });
        long effectiveTimeoutNanos = timeoutNanos();
        try
        {
            executor.execute(task);
            if (remainingBudgetNanos != null)
            {
                long remaining = remainingBudgetNanos.getAsLong();
                if (remaining <= 0L)
                {
                    if (!task.isDone())
                    {
                        task.cancel(true);
                        log.warn("总览数据源排队后预算已耗尽: source={}", source);
                        return OverviewSourceResult.unavailable(source,
                                "aggregation budget exhausted");
                    }
                    // 任务已在预算耗尽前完成：保留有效结果，不因预算检查丢弃。
                }
                else
                {
                    effectiveTimeoutNanos = Math.min(effectiveTimeoutNanos, remaining);
                }
            }
            T value = task.get(effectiveTimeoutNanos, TimeUnit.NANOSECONDS);
            // 显式谓词优先：聚合服务传 value -> false，才能把“键存在但列表为空”
            // 解释为真实 0，由服务层决定 OK 0 还是 NO_DATA。
            boolean empty = value == null
                    || (emptyPredicate == null ? isEmptyResult(value) : emptyPredicate.test(value));
            if (empty)
            {
                // 源可用但没有证据：返回 NO_DATA，由调用方显式表达，不写 0。
                return OverviewSourceResult.noData(source);
            }
            return OverviewSourceResult.ok(source, value);
        }
        catch (TimeoutException e)
        {
            task.cancel(true);
            log.warn("总览数据源超时: source={}, timeoutMs={}", source,
                    TimeUnit.NANOSECONDS.toMillis(effectiveTimeoutNanos));
            return OverviewSourceResult.unavailable(source, "timeout");
        }
        catch (InterruptedException e)
        {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("总览数据源调用被中断: source={}", source);
            return OverviewSourceResult.unavailable(source, "interrupted");
        }
        catch (CancellationException e)
        {
            log.warn("总览数据源 FutureTask 已取消: source={}", source);
            return OverviewSourceResult.unavailable(source, "cancelled");
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BudgetExhaustedException)
            {
                log.warn("总览数据源执行前预算已耗尽: source={}", source);
                return OverviewSourceResult.unavailable(source,
                        "aggregation budget exhausted");
            }
            log.warn("总览数据源异常: source={}, cause={}", source, cause.toString());
            // 用全限定名，避免匿名类 getSimpleName() 为空导致 UNAVAILABLE 构造失败。
            return OverviewSourceResult.unavailable(source, cause.getClass().getName());
        }
        catch (RejectedExecutionException e)
        {
            log.warn("总览数据源执行器拒绝任务: source={}", source);
            return OverviewSourceResult.unavailable(source, "executor rejected");
        }
        catch (RuntimeException e)
        {
            // 空值判定等回调也可能抛异常；同样收敛为源不可用，不能向上冒泡。
            log.warn("总览数据源契约异常: source={}, cause={}", source, e.toString());
            return OverviewSourceResult.unavailable(source, "contract violation");
        }
    }

    private long timeoutNanos()
    {
        try
        {
            return timeout.toNanos();
        }
        catch (ArithmeticException e)
        {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 预算耗尽标记。只在本类内部由源线程抛出，并由 ExecutionException 分支收敛，
     * 不进入全局异常处理器。
     */
    private static final class BudgetExhaustedException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 判断结果是否为空集合/空 Map。
     *
     * <p>空集合表示没有证据，不是真实测量值 0；默认映射 NO_DATA，
     * 避免调用方漏传 emptyPredicate 时把空列表当成功结果。</p>
     *
     * @param value 数据源结果
     * @return true 表示空 Collection 或空 Map
     */
    private static boolean isEmptyResult(Object value)
    {
        return (value instanceof Collection<?> collection && collection.isEmpty())
                || (value instanceof Map<?, ?> map && map.isEmpty());
    }
}
