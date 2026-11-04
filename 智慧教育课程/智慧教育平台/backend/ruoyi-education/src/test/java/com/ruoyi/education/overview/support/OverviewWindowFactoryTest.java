package com.ruoyi.education.overview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 时间窗参数解析测试。
 */
class OverviewWindowFactoryTest
{
    private final OverviewWindowFactory factory = new OverviewWindowFactory(
            Clock.fixed(Instant.parse("2026-10-30T06:00:00Z"), ZoneOffset.UTC));

    @Test
    void defaultWindowUsesCurrentUtcDay()
    {
        OverviewWindow window = factory.fromQuery(null, null);

        assertEquals(Instant.parse("2026-10-30T00:00:00Z"), window.startsAt());
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), window.endsAt());
    }

    @Test
    void parsesExplicitWindow()
    {
        OverviewWindow window = factory.fromQuery("2026-10-01T00:00:00Z",
                "2026-10-02T00:00:00Z");

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), window.startsAt());
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), window.endsAt());
    }

    @Test
    void rejectsSingleBound()
    {
        assertThrows(OverviewValidationException.class,
                () -> factory.fromQuery("2026-10-01T00:00:00Z", null));
    }

    @Test
    void rejectsInvalidFormat()
    {
        assertThrows(OverviewValidationException.class,
                () -> factory.fromQuery("not-a-time", "2026-10-02T00:00:00Z"));
    }
}
