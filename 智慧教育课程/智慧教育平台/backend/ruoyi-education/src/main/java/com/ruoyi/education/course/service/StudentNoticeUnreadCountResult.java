package com.ruoyi.education.course.service;

import java.time.LocalDateTime;

/**
 * 学生通知未读数结果。
 */
public record StudentNoticeUnreadCountResult(long unreadCount, LocalDateTime asOf)
{
}
