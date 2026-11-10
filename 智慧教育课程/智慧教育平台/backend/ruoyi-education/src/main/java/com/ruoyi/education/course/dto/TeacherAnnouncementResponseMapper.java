package com.ruoyi.education.course.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.policy.AnnouncementStatePolicy;

/**
 * 将公告实体转换为教师端响应 DTO。ID 转字符串，时间统一输出 UTC 毫秒。
 */
public final class TeacherAnnouncementResponseMapper
{
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private TeacherAnnouncementResponseMapper()
    {
    }

    public static TeacherAnnouncementListItemResponse toListItem(CourseAnnouncement announcement,
            LocalDateTime now)
    {
        return new TeacherAnnouncementListItemResponse(
                id(announcement.getId()),
                id(announcement.getBindingId()),
                announcement.getTitle(),
                announcement.getStatus().name(),
                visibility(announcement, now),
                time(announcement.getOpensAt()),
                time(announcement.getClosesAt()),
                id(announcement.getCreatedByUserId()),
                id(announcement.getPublishedByUserId()),
                time(announcement.getCreatedAt()));
    }

    public static TeacherAnnouncementDetailResponse toDetail(CourseAnnouncement announcement,
            LocalDateTime now)
    {
        return new TeacherAnnouncementDetailResponse(
                id(announcement.getId()),
                id(announcement.getBindingId()),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getStatus().name(),
                visibility(announcement, now),
                time(announcement.getOpensAt()),
                time(announcement.getClosesAt()),
                id(announcement.getCreatedByUserId()),
                id(announcement.getPublishedByUserId()),
                time(announcement.getCreatedAt()));
    }

    private static String visibility(CourseAnnouncement announcement, LocalDateTime now)
    {
        return AnnouncementStatePolicy.visibility(
                announcement.getStatus(), announcement.getOpensAt(), announcement.getClosesAt(), now).name();
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
