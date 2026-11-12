package com.ruoyi.education.course.dto;

/**
 * 教师公告详情响应，包含正文和服务端计算的可见状态。
 */
public record TeacherAnnouncementDetailResponse(
        String id,
        String bindingId,
        String title,
        String body,
        String status,
        String visibilityState,
        String opensAt,
        String closesAt,
        String createdByUserId,
        String publishedByUserId,
        String createdAt)
{
}
