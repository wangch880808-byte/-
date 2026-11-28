package com.ruoyi.education.overview.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 聚合项状态与值组合的契约测试。
 */
class OverviewItemTest
{
    @Test
    void okZeroIsMeasuredZeroNotNoData()
    {
        OverviewItem item = OverviewItem.ok("teacher.class.count", BigDecimal.ZERO,
                "COUNT", "course", 0L, "/teacher/classes");

        assertEquals(OverviewStatus.OK, item.status());
        assertEquals(BigDecimal.ZERO, item.value());
        assertEquals(0L, item.sampleCount());
    }

    @Test
    void noDataHasNullValue()
    {
        OverviewItem item = OverviewItem.noData("teacher.class.count", "COUNT",
                "course", null);

        assertEquals(OverviewStatus.NO_DATA, item.status());
        assertNull(item.value());
        assertNull(item.sampleCount());
    }

    @Test
    void unavailableHasNullValue()
    {
        OverviewItem item = OverviewItem.unavailable("teacher.class.count", "COUNT",
                "course", null);

        assertEquals(OverviewStatus.UNAVAILABLE, item.status());
        assertNull(item.value());
        assertNull(item.sampleCount());
    }

    @Test
    void okWithoutValueIsRejected()
    {
        assertThrows(OverviewValidationException.class, () -> new OverviewItem(
                "teacher.class.count", null, "COUNT", OverviewStatus.OK,
                "course", null, null));
    }

    @Test
    void noDataWithValueIsRejected()
    {
        assertThrows(OverviewValidationException.class, () -> new OverviewItem(
                "teacher.class.count", BigDecimal.ONE, "COUNT", OverviewStatus.NO_DATA,
                "course", null, null));
    }

    @Test
    void negativeSampleCountIsRejected()
    {
        assertThrows(OverviewValidationException.class, () -> OverviewItem.ok(
                "teacher.class.count", BigDecimal.ONE, "COUNT", "course", -1L, null));
    }
}
