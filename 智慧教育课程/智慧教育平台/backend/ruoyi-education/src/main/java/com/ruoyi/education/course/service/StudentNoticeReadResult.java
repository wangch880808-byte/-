package com.ruoyi.education.course.service;

import java.time.LocalDateTime;

/**
 * 单条通知标记已读结果。
 */
public record StudentNoticeReadResult(Long id, LocalDateTime readAt, boolean alreadyRead)
{
}
