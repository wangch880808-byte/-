package com.ruoyi.education.course.dto;

/**
 * 学生公告详情响应，包含正文。
 */
public record StudentAnnouncementDetailResponse(
        String id,
        String bindingId,
        String title,
        String body,
        String opensAt,
        String closesAt,
        String createdAt)
{
}
