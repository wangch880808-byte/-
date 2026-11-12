package com.ruoyi.education.course.dto;

/**
 * 学生公告列表行：只包含契约规定的摘要字段。
 */
public record StudentAnnouncementListItemResponse(
        String id,
        String bindingId,
        String title,
        String opensAt,
        String closesAt,
        String createdAt)
{
}
