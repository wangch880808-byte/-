package com.ruoyi.education.course.dto;

/**
 * 全部通知标记已读响应。
 */
public record StudentNoticeReadAllResponse(int markedCount, long unreadCount, String operatedAt)
{
}
