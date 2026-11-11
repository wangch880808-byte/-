package com.ruoyi.education.course.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ruoyi.education.course.domain.CourseAnnouncement;

/**
 * 学生公告响应 DTO 转换。ID 转字符串，时间统一输出 UTC 毫秒。
 */
public final class StudentAnnouncementResponseMapper
{
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private StudentAnnouncementResponseMapper()
    {
    }

    public static StudentAnnouncementListItemResponse toListItem(CourseAnnouncement announcement)
    {
        return new StudentAnnouncementListItemResponse(
                id(announcement.getId()),
                id(announcement.getBindingId()),
                announcement.getTitle(),
                time(announcement.getOpensAt()),
                time(announcement.getClosesAt()),
                time(announcement.getCreatedAt()));
    }

    public static StudentAnnouncementDetailResponse toDetail(CourseAnnouncement announcement)
    {
        return new StudentAnnouncementDetailResponse(
                id(announcement.getId()),
                id(announcement.getBindingId()),
                announcement.getTitle(),
                announcement.getBody(),
                time(announcement.getOpensAt()),
                time(announcement.getClosesAt()),
                time(announcement.getCreatedAt()));
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
