package com.ruoyi.education.course.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.service.StudentNoticeReadAllResult;
import com.ruoyi.education.course.service.StudentNoticeReadResult;
import com.ruoyi.education.course.service.StudentNoticeUnreadCountResult;

/**
 * 学生通知响应 DTO 转换。ID 转字符串，时间统一输出 UTC 毫秒。
 */
public final class StudentNoticeResponseMapper
{
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private StudentNoticeResponseMapper()
    {
    }

    public static StudentNoticeResponse toResponse(StudentNotice notice)
    {
        return new StudentNoticeResponse(
                id(notice.getId()),
                notice.getNoticeKind() == null ? null : notice.getNoticeKind().name(),
                id(notice.getBindingId()),
                id(notice.getSourceId()),
                notice.getTitle(),
                time(notice.getVisibleAt()),
                time(notice.getReadAt()),
                notice.getReadAt() != null,
                time(notice.getCreatedAt()));
    }

    public static StudentNoticeUnreadCountResponse toUnreadCount(StudentNoticeUnreadCountResult result)
    {
        return new StudentNoticeUnreadCountResponse(result.unreadCount(), time(result.asOf()));
    }

    public static StudentNoticeReadResponse toRead(StudentNoticeReadResult result)
    {
        return new StudentNoticeReadResponse(
                id(result.id()), time(result.readAt()), result.alreadyRead());
    }

    public static StudentNoticeReadAllResponse toReadAll(StudentNoticeReadAllResult result)
    {
        return new StudentNoticeReadAllResponse(
                result.markedCount(), result.unreadCount(), time(result.operatedAt()));
    }

    private static String id(Long value)
    {
        return value == null ? null : value.toString();
    }

    private static String time(LocalDateTime value)
    {
        return value == null ? null : value.format(UTC_MILLIS);
    }
}
