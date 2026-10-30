package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import com.ruoyi.education.course.domain.CourseAnnouncement;

/**
 * 公告发布 fan-out 端口。
 *
 * <p>公告服务在发布事务内调用该端口。实现方必须与调用方共享事务，
 * 在授权或写入失败时向上抛出业务异常，由公告发布事务整体回滚。</p>
 */
public interface AnnouncementNoticeFanoutPort
{
    /**
     * 为一次实际完成 {@code DRAFT -> PUBLISHED} 的公告生成学生通知。
     *
     * @param announcement 已发布公告，必须包含 ID、绑定、标题和 opensAt
     * @param publishedAt 公告发布动作的服务端 UTC 时间，用于入班区间判断
     * @return 数据库返回的处理行数；MySQL 默认驱动可能把重复键计为匹配行，
     *         幂等以通知行数不增和 readAt 不变为准
     * @throws com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException
     *         授权适配器缺失、异常、返回 null、跨绑定或字段不完整；映射为 503
     * @throws com.ruoyi.education.course.exception.NoticePersistenceException
     *         公告数据不完整或通知写入失败；映射为 500
     */
    int fanOutAnnouncement(CourseAnnouncement announcement, LocalDateTime publishedAt);
}
