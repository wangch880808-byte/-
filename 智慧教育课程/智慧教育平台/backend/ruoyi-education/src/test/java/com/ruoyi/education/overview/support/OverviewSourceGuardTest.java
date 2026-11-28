package com.ruoyi.education.overview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.overview.dto.OverviewStatus;

/**
 * 数据源三态映射测试：OK、NO_DATA、UNAVAILABLE 与失败收敛。
 */
class OverviewSourceGuardTest
{
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor()
    {
        executor.shutdownNow();
    }

    @Test
    void okSourceReturnsOk()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("course", () -> "value");

        assertEquals(OverviewStatus.OK, result.status());
        assertEquals("value", result.value());
        assertNull(result.failureReason());
    }

    @Test
    void nullValueMapsToNoData()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("course", () -> null);

        assertEquals(OverviewStatus.NO_DATA, result.status());
        assertNull(result.value());
    }

    @Test
    void emptyCollectionMapsToNoDataByDefault()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<List<String>> result = guard.query("course", List::of);

        assertEquals(OverviewStatus.NO_DATA, result.status());
        assertNull(result.value());
    }

    @Test
    void explicitPredicateCanKeepEmptyCollectionAsOk()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<List<String>> result = guard.query("course", List::of,
                value -> false);

        assertEquals(OverviewStatus.OK, result.status());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void emptyCollectionMapsToNoDataWithPredicate()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<List<String>> result = guard.query("course",
                List::of, List::isEmpty);

        assertEquals(OverviewStatus.NO_DATA, result.status());
        assertNull(result.value());
    }

    @Test
    void missingAdapterMapsToUnavailable()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("notice", null);

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertEquals("adapter missing", result.failureReason());
        assertNull(result.value());
    }

    @Test
    void sourceExceptionMapsToUnavailable()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("course", () ->
        {
            throw new IllegalStateException("boom");
        });

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertEquals("java.lang.IllegalStateException", result.failureReason());
        assertNull(result.value());
    }

    @Test
    void timeoutMapsToUnavailable()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(30));

        OverviewSourceResult<String> result = guard.query("course", () ->
        {
            try
            {
                Thread.sleep(300);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            return "late";
        });

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertEquals("timeout", result.failureReason());
        assertNull(result.value());
    }

    @Test
    void rejectedExecutionMapsToUnavailable()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(command ->
        {
            throw new RejectedExecutionException("full");
        }, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("course", () -> "value");

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertEquals("executor rejected", result.failureReason());
        assertNull(result.value());
    }

    @Test
    void budgetExpiredAfterQueueSkipsSupplierBeforePortCall() throws Exception
    {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        Executor queueingExecutor = queued::set;
        OverviewSourceGuard guard = new OverviewSourceGuard(queueingExecutor,
                Duration.ofMillis(500));
        AtomicInteger budgetChecks = new AtomicInteger();
        CountDownLatch afterSubmitCheck = new CountDownLatch(1);
        LongSupplier budget = () ->
        {
            int check = budgetChecks.incrementAndGet();
            if (check == 2)
            {
                afterSubmitCheck.countDown();
            }
            return check <= 2 ? 500_000_000L : 0L;
        };
        AtomicInteger portCalls = new AtomicInteger();

        CompletableFuture<OverviewSourceResult<String>> future = CompletableFuture.supplyAsync(
                () -> guard.query("course", () -> {
                    portCalls.incrementAndGet();
                    return "value";
                }, null, budget));

        assertTrue(afterSubmitCheck.await(2, TimeUnit.SECONDS));
        Runnable task = queued.get();
        assertTrue(task != null);
        task.run();

        OverviewSourceResult<String> result = future.get(2, TimeUnit.SECONDS);

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertEquals(0, portCalls.get());
    }

    @Test
    void timeoutDoesNotProveBlockingSupplierStopped() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean supplierFinished = new AtomicBoolean(false);
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(50));

        CompletableFuture<OverviewSourceResult<String>> future = CompletableFuture.supplyAsync(
                () -> guard.query("course", () ->
                {
                    started.countDown();
                    while (true)
                    {
                        try
                        {
                            if (release.await(10, TimeUnit.MILLISECONDS))
                            {
                                break;
                            }
                        }
                        catch (InterruptedException e)
                        {
                            // 模拟不响应中断的阻塞 I/O：超时后仍继续执行。
                        }
                    }
                    supplierFinished.set(true);
                    return "late";
                }));

        try
        {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            OverviewSourceResult<String> result = future.get(2, TimeUnit.SECONDS);

            assertEquals(OverviewStatus.UNAVAILABLE, result.status());
            assertFalse(supplierFinished.get());
        }
        finally
        {
            release.countDown();
        }
    }

    @Test
    void emptyPredicateExceptionMapsToUnavailable()
    {
        OverviewSourceGuard guard = new OverviewSourceGuard(executor, Duration.ofMillis(200));

        OverviewSourceResult<String> result = guard.query("course", () -> "value", value ->
        {
            throw new IllegalArgumentException("bad predicate");
        });

        assertEquals(OverviewStatus.UNAVAILABLE, result.status());
        assertTrue(result.failureReason().contains("contract"));
        assertNull(result.value());
    }
}
