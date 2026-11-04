package com.ruoyi.education.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * ISO-8601 时间格式化（课堂 API 契约）。
 */
public final class EducationDateUtils
{
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private EducationDateUtils()
    {
    }

    public static String toIsoString(Date date)
    {
        if (date == null)
        {
            return null;
        }
        return ISO_FORMATTER.format(date.toInstant());
    }

    public static String nowIsoString()
    {
        return ISO_FORMATTER.format(Instant.now());
    }
}
