package com.ruoyi.education.course.dto;

/**
 * 教师创建公告草稿的请求体。ID 和时间均按字符串传输，由服务端解析。
 */
public record TeacherAnnouncementCreateRequest(
        String bindingId,
        String title,
        String body,
        String opensAt,
        String closesAt)
{
}
