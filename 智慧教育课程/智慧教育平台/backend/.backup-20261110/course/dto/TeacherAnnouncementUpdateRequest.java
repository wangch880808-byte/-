package com.ruoyi.education.course.dto;

/**
 * 教师完整覆盖编辑公告草稿的请求体。bindingId 不可修改，因此不在请求中。
 */
public record TeacherAnnouncementUpdateRequest(
        String title,
        String body,
        String opensAt,
        String closesAt)
{
}
