package com.ruoyi.education.course.dto;

/**
 * 学生通知列表/详情响应。通知只作为提醒，不返回来源正文。
 */
public record StudentNoticeResponse(
        String id,
        String kind,
        String bindingId,
        String sourceId,
        String title,
        String visibleAt,
        String readAt,
        boolean isRead,
        String createdAt)
{
}
