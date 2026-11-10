package com.ruoyi.education.course.domain;

/**
 * 公告在某一服务端时刻的可见状态。
 *
 * <p>该值是计算态，不写入数据库；数据库只存 {@link AnnouncementStatus}、
 * opens_at 和 closes_at。</p>
 */
public enum AnnouncementVisibility
{
    DRAFT,
    NOT_YET_OPEN,
    OPEN,
    CLOSED,
    ARCHIVED
}
