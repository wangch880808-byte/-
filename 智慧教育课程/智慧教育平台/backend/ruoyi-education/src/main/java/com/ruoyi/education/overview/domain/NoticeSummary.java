package com.ruoyi.education.overview.domain;

import java.time.Instant;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 本人未读通知汇总。
 *
 * <p>只含未读数量，不含通知正文、来源对象或已读明细。0 未读是真实值，
 * 调用方应映射为 OK；适配器缺失时必须映射为 UNAVAILABLE，不能返回 0。</p>
 *
 * @param recipientUserId 接收人用户 ID
 * @param unreadCount     未读数量，最小 0
 * @param asOf            统计截止时间
 */
public record NoticeSummary(Long recipientUserId, long unreadCount, Instant asOf)
{
    /**
     * 校验接收人、计数与截止时间。
     *
     * @throws OverviewValidationException 接收人为空、计数为负数或截止时间为空
     */
    public NoticeSummary
    {
        if (recipientUserId == null)
        {
            throw new OverviewValidationException("recipientUserId 不能为空");
        }
        if (unreadCount < 0)
        {
            throw new OverviewValidationException("unreadCount 不能为负数");
        }
        if (asOf == null)
        {
            throw new OverviewValidationException("asOf 不能为空");
        }
    }
}
