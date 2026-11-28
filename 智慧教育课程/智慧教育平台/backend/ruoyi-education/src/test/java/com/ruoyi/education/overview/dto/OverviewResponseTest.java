package com.ruoyi.education.overview.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 总览响应快照与空数组约束测试。
 */
class OverviewResponseTest
{
    private static final OverviewWindow WINDOW = OverviewWindow.of(
            Instant.parse("2026-10-30T00:00:00Z"),
            Instant.parse("2026-10-31T00:00:00Z"));

    @Test
    void copiesItemsSoCallerCannotMutateResponse()
    {
        List<OverviewItem> items = new ArrayList<>();
        items.add(OverviewItem.ok("teacher.class.count", BigDecimal.ONE, "COUNT",
                "course", 1L, null));

        OverviewResponse response = OverviewResponse.of(
                Instant.parse("2026-10-30T06:00:00Z"), WINDOW, items);
        items.clear();

        assertEquals(1, response.items().size());
    }

    @Test
    void acceptsNoDataAndUnavailableItems()
    {
        OverviewResponse response = OverviewResponse.of(
                Instant.parse("2026-10-30T06:00:00Z"), WINDOW,
                List.of(
                        OverviewItem.noData("teacher.class.count", "COUNT", "course", null),
                        OverviewItem.unavailable("teacher.notice.unread", "COUNT",
                                "notice", null)));

        assertEquals(2, response.items().size());
        assertEquals(OverviewStatus.NO_DATA, response.items().get(0).status());
        assertEquals(OverviewStatus.UNAVAILABLE, response.items().get(1).status());
    }

    @Test
    void emptyItemsAreRejected()
    {
        assertThrows(OverviewValidationException.class, () -> OverviewResponse.of(
                Instant.parse("2026-10-30T06:00:00Z"), WINDOW, List.of()));
    }

    @Test
    void nullItemIsRejected()
    {
        List<OverviewItem> items = new ArrayList<>();
        items.add(null);

        assertThrows(OverviewValidationException.class, () -> OverviewResponse.of(
                Instant.parse("2026-10-30T06:00:00Z"), WINDOW, items));
    }
}
