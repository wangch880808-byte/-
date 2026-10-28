package com.ruoyi.education.course.service;

import java.time.LocalDateTime;

/**
 * 全部通知标记已读结果。
 */
public record StudentNoticeReadAllResult(int markedCount, long unreadCount, LocalDateTime operatedAt)
{
}
