package com.ruoyi.education.course.support;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 统一公告时间的数据库精度规则：先四舍五入到毫秒，再进入校验或持久化。
 */
public final class DatabaseTimePrecision
{
    private static final long HALF_MILLISECOND_NANOS = 500_000L;

    private DatabaseTimePrecision()
    {
    }

    public static LocalDateTime toMillisecondPrecision(LocalDateTime value)
    {
        if (value == null)
        {
            return null;
        }
        return value.plusNanos(HALF_MILLISECOND_NANOS).truncatedTo(ChronoUnit.MILLIS);
    }
}
