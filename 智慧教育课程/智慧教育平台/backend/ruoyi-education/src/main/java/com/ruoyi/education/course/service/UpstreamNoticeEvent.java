package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.education.course.domain.NoticeKind;

/**
 * 上游个人通知事件。
 *
 * <p>字段语义按接收侧内部契约定义，待与第 3、4、5 组联调确认：
 * {@code eventId} 仅用于日志排查，不落库；幂等由通知唯一键
 * {@code (recipient_user_id, notice_kind, source_id)} 保证。</p>
 *
 * @param eventId 上游事件唯一标识，非空；用于日志排查
 * @param noticeKind 通知类型，只能是 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE
 * @param bindingId 课程—教学班绑定 ID，正整数
 * @param sourceId 来源业务对象 ID：PREVIEW/ASSIGNMENT 为 assignment.id，
 *                 ACTIVITY 为 activity_instance.id，GRADE 为 grade_publication.id
 * @param title 接收时标题快照，1–200 字符
 * @param contentSnapshot 可选内容快照；当前通知表无正文字段，不落库
 * @param occurredAt 事件发生时间（UTC），用于在班区间判断和 createdAt
 * @param visibleAt 可选学生可见时间（UTC）；为空时取 occurredAt
 * @param recipientUserIds 为 null 时按教学班在班学生投递；为空列表表示明确无接收人；
 *                         非空列表按指定学生投递并直接校验字段完整性
 */
public record UpstreamNoticeEvent(
        String eventId,
        NoticeKind noticeKind,
        Long bindingId,
        Long sourceId,
        String title,
        String contentSnapshot,
        LocalDateTime occurredAt,
        LocalDateTime visibleAt,
        List<Long> recipientUserIds)
{
}
