package com.ruoyi.education.group3.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;

/**
 * The database stores UTC DATETIME(3). API payloads use ISO-8601 UTC strings.
 */
public final class Group3Time
{
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private Group3Time()
    {
    }

    public static LocalDateTime nowUtc()
    {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static String nowIso()
    {
        return Instant.now().toString();
    }

    public static LocalDateTime parseUtc(String value, String fieldName)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        }
        catch (DateTimeParseException ignored)
        {
            try
            {
                return LocalDateTime.parse(value).atZone(DISPLAY_ZONE)
                        .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
            catch (DateTimeParseException error)
            {
                throw new ServiceException(fieldName + "不是有效时间", HttpStatus.BAD_REQUEST);
            }
        }
    }

    public static String format(Timestamp timestamp)
    {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    public static void normalizeTimes(Map<String, Object> row)
    {
        if (row == null)
        {
            return;
        }
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Timestamp timestamp)
            {
                entry.setValue(format(timestamp));
            }
        }
    }

    public static void normalizeTimes(List<Map<String, Object>> rows)
    {
        rows.forEach(Group3Time::normalizeTimes);
    }

    public static Map<String, Object> withServerTime(Map<String, Object> body)
    {
        Map<String, Object> result = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        normalizeTimes(result);
        result.put("serverTime", nowIso());
        return result;
    }
}
