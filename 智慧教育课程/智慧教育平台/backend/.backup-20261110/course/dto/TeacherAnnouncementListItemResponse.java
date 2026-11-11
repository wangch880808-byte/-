package com.ruoyi.education.course.dto;

/**
 * 教师公告列表行，不包含正文。
 */
public record TeacherAnnouncementListItemResponse(
        String id,
        String bindingId,
        String title,
        String status,
        String visibilityState,
        String opensAt,
        String closesAt,
        String createdByUserId,
        String publishedByUserId,
        String createdAt)
{
}
