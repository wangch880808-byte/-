package com.ruoyi.education.overview.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 时间窗半开区间与参数校验测试。
 */
class OverviewWindowTest
{
    @Test
    void acceptsHalfOpenWindow()
    {
        OverviewWindow window = OverviewWindow.of(
                Instant.parse("2026-10-30T00:00:00Z"),
                Instant.parse("2026-10-31T00:00:00Z"));

        assertEquals(Instant.parse("2026-10-30T00:00:00Z"), window.startsAt());
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), window.endsAt());
    }

    @Test
    void rejectsEmptyWindow()
    {
        assertThrows(OverviewValidationException.class, () -> OverviewWindow.of(
                Instant.parse("2026-10-30T00:00:00Z"),
                Instant.parse("2026-10-30T00:00:00Z")));
    }

    @Test
    void rejectsReversedWindow()
    {
        assertThrows(OverviewValidationException.class, () -> OverviewWindow.of(
                Instant.parse("2026-10-31T00:00:00Z"),
                Instant.parse("2026-10-30T00:00:00Z")));
    }
}
