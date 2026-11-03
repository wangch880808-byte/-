package com.ruoyi.education.course.dto;

import java.util.Locale;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.exception.NoticeValidationException;

/**
 * 学生通知 HTTP 参数的受控解析。非法输入统一转为 400 业务异常。
 */
public final class StudentNoticeRequestParser
{
    private StudentNoticeRequestParser()
    {
    }

    public static Long optionalPositiveId(String value, String fieldName)
    {
        if (value == null)
        {
            return null;
        }
        if (value.isBlank())
        {
            throw new NoticeValidationException(fieldName + " 不能为空字符串");
        }
        try
        {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0)
            {
                throw new NoticeValidationException(fieldName + " 必须是正整数");
            }
            return parsed;
        }
        catch (NumberFormatException e)
        {
            throw new NoticeValidationException(fieldName + " 必须是正整数");
        }
    }

    public static NoticeKind optionalKind(String value)
    {
        if (value == null)
        {
            return null;
        }
        if (value.isBlank())
        {
            throw new NoticeValidationException("kind 不能为空字符串");
        }
        try
        {
            return NoticeKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new NoticeValidationException(
                    "kind 必须是 ANNOUNCEMENT、PREVIEW、ACTIVITY、ASSIGNMENT 或 GRADE");
        }
    }

    public static boolean optionalBoolean(String value, String fieldName, boolean defaultValue)
    {
        if (value == null)
        {
            return defaultValue;
        }
        if (value.isBlank())
        {
            throw new NoticeValidationException(fieldName + " 不能为空字符串");
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed))
        {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed))
        {
            return false;
        }
        throw new NoticeValidationException(fieldName + " 必须是 true 或 false");
    }
}
