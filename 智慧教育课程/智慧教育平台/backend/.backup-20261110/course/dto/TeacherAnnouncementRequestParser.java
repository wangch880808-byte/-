package com.ruoyi.education.course.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.exception.AnnouncementValidationException;

/**
 * 教师公告 HTTP 参数的受控解析。非法输入统一转为 400 业务异常。
 */
public final class TeacherAnnouncementRequestParser
{
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private TeacherAnnouncementRequestParser()
    {
    }

    public static Long requiredId(String value, String fieldName)
    {
        if (value == null || value.isBlank())
        {
            throw new AnnouncementValidationException(fieldName + " 不能为空");
        }
        try
        {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0)
            {
                throw new AnnouncementValidationException(fieldName + " 必须是正整数");
            }
            return parsed;
        }
        catch (NumberFormatException e)
        {
            throw new AnnouncementValidationException(fieldName + " 必须是正整数");
        }
    }

    public static AnnouncementStatus optionalStatus(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return AnnouncementStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new AnnouncementValidationException("status 必须是 DRAFT、PUBLISHED 或 ARCHIVED");
        }
    }

    public static int pageNumber(String value, String fieldName, int defaultValue, int min, int max)
    {
        if (value == null || value.isBlank())
        {
            return defaultValue;
        }
        int parsed;
        try
        {
            parsed = Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            throw new AnnouncementValidationException(fieldName + " 必须是整数");
        }
        if (parsed < min || parsed > max)
        {
            if (max == Integer.MAX_VALUE)
            {
                throw new AnnouncementValidationException(fieldName + " 不能小于 " + min);
            }
            throw new AnnouncementValidationException(fieldName + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return parsed;
    }

    public static LocalDateTime optionalTime(String value, String fieldName)
    {
        if (value == null)
        {
            return null;
        }
        if (value.isBlank())
        {
            throw new AnnouncementValidationException(fieldName + " 不能为空字符串");
        }
        try
        {
            OffsetDateTime parsed = OffsetDateTime.parse(value.trim(), RFC3339);
            if (parsed.getNano() % 1_000_000 != 0)
            {
                throw new AnnouncementValidationException(fieldName + " 必须是毫秒精度");
            }
            return parsed.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
        catch (DateTimeParseException e)
        {
            throw new AnnouncementValidationException(fieldName + " 必须是 RFC3339 时间");
        }
    }
}
