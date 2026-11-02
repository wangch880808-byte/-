package com.ruoyi.education.course.dto;

/**
 * 单条通知标记已读响应。
 */
public record StudentNoticeReadResponse(String id, String readAt, boolean alreadyRead)
{
}
