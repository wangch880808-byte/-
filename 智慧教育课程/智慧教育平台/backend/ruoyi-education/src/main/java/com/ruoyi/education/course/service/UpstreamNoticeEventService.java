package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.StudentAnnouncementMember;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticePersistenceException;
import com.ruoyi.education.course.exception.NoticeValidationException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 上游个人通知事件接收服务。
 *
 * <p>接收 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件，按教学班或指定学生写入
 * edu_user_notice。单事件写入在同一事务内完成，按 500 条分批；唯一键
 * {@code (recipient_user_id, notice_kind, source_id)} 保证重复投递幂等，
 * 不覆盖已有 readAt。当前不提供 HTTP 接收端点，仅内部 Spring 端口。</p>
 */
@Service
public class UpstreamNoticeEventService implements UpstreamNoticeEventPort
{
    private static final Logger log = LoggerFactory.getLogger(UpstreamNoticeEventService.class);

    /**
     * 单批接收人数上限；降低单语句锁持有时间，并限制与 read-all 当前读
     * gap lock 的等待范围。
     */
    private static final int BATCH_SIZE = 500;

    private final StudentAnnouncementAuthorizationPort authorizationPort;

    private final StudentNoticeMapper noticeMapper;

    /**
     * Spring 构造器。学生授权适配器缺失时仍可创建服务，但按教学班投递会
     * 返回 503；指定学生列表投递不依赖该适配器。
     *
     * @param authorizationPortProvider 学生授权适配器提供者
     * @param noticeMapper 通知 Mapper
     */
    @Autowired
    public UpstreamNoticeEventService(
            ObjectProvider<StudentAnnouncementAuthorizationPort> authorizationPortProvider,
            StudentNoticeMapper noticeMapper)
    {
        this(authorizationPortProvider.getIfAvailable(), noticeMapper);
    }

    UpstreamNoticeEventService(StudentAnnouncementAuthorizationPort authorizationPort,
            StudentNoticeMapper noticeMapper)
    {
        this.authorizationPort = authorizationPort;
        this.noticeMapper = Objects.requireNonNull(noticeMapper, "noticeMapper");
    }

    /**
     * 接收一条上游事件并写入个人通知。
     *
     * <p>事件按教学班投递时，使用事件发生时间查询当前有效学生并过滤
     * {@code [joinedAt, leftAt)}；指定学生列表投递时只校验字段完整性，
     * 不读取第 2 组成员表。重复投递由唯一键幂等处理。</p>
     *
     * @param event 上游事件
     * @return 数据库返回的处理行数；MySQL 默认驱动可能把重复键计为匹配行，
     *         幂等以通知行数不增和 readAt 不变为准
     * @throws NoticeValidationException 事件字段不完整或类型非法
     * @throws NoticeAuthorizationUnavailableException 教学班投递授权不可用
     * @throws NoticePersistenceException 通知写入失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int receive(UpstreamNoticeEvent event)
    {
        validateEvent(event);
        if (log.isDebugEnabled())
        {
            log.debug("接收上游通知事件 eventId={}, kind={}, sourceId={}",
                    event.eventId(), event.noticeKind(), event.sourceId());
        }

        LocalDateTime occurredAt = DatabaseTimePrecision.toMillisecondPrecision(event.occurredAt());
        LocalDateTime visibleAt = DatabaseTimePrecision.toMillisecondPrecision(
                event.visibleAt() != null ? event.visibleAt() : occurredAt);
        List<Long> recipients = event.recipientUserIds() == null
                ? resolveClassRecipients(event.bindingId(), occurredAt)
                : normalizeExplicitRecipients(event.recipientUserIds());
        if (recipients.isEmpty())
        {
            return 0;
        }

        // 事件 ID 仅用于日志排查；幂等以数据库唯一键为准，不依赖 eventId 落库。
        // 同事务内任一批失败即回滚全部通知，不产生部分写入。
        int inserted = 0;
        for (int from = 0; from < recipients.size(); from += BATCH_SIZE)
        {
            int to = Math.min(from + BATCH_SIZE, recipients.size());
            inserted += insertBatch(event, visibleAt, occurredAt, recipients.subList(from, to));
        }
        return inserted;
    }

    /**
     * 按教学班事件发生时间解析在班学生。授权缺失/异常/结果不完整返回 503。
     */
    private List<Long> resolveClassRecipients(Long bindingId, LocalDateTime occurredAt)
    {
        // TODO 生产学生授权适配器尚未接入；第 2 组提供绑定成员契约后解除。
        // 该分支必须失败关闭，不能直接查询第 2 组私有成员表。
        if (authorizationPort == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权服务尚未接入");
        }

        List<StudentAnnouncementMember> members;
        try
        {
            members = authorizationPort.listActiveStudents(bindingId, occurredAt);
        }
        catch (RuntimeException e)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权查询失败", e);
        }
        if (members == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不完整");
        }

        Set<Long> recipients = new LinkedHashSet<>();
        for (StudentAnnouncementMember member : members)
        {
            validateMember(member, bindingId);
            if (!member.authority().active())
            {
                continue;
            }
            LocalDateTime joinedAt = DatabaseTimePrecision.toMillisecondPrecision(member.joinedAt());
            LocalDateTime leftAt = DatabaseTimePrecision.toMillisecondPrecision(member.leftAt());
            // 事件时点必须落在 [joinedAt, leftAt)：等于 joinedAt 可接收，
            // 等于 leftAt 不可接收。
            if (occurredAt.isBefore(joinedAt))
            {
                continue;
            }
            if (leftAt != null && !occurredAt.isBefore(leftAt))
            {
                continue;
            }
            recipients.add(member.userId());
        }
        return new ArrayList<>(recipients);
    }

    /**
     * 指定学生列表投递：只校验字段完整性，不查询成员表。
     * 空列表表示明确无接收人，返回空集合。
     */
    private List<Long> normalizeExplicitRecipients(List<Long> recipientUserIds)
    {
        Set<Long> recipients = new LinkedHashSet<>();
        for (Long userId : recipientUserIds)
        {
            if (userId == null || userId <= 0)
            {
                throw new NoticeValidationException("recipientUserIds 含非法学生 ID");
            }
            recipients.add(userId);
        }
        return new ArrayList<>(recipients);
    }

    private void validateEvent(UpstreamNoticeEvent event)
    {
        if (event == null
                || event.eventId() == null || event.eventId().isBlank()
                || event.noticeKind() == null
                || event.noticeKind() == NoticeKind.ANNOUNCEMENT
                || event.bindingId() == null || event.bindingId() <= 0
                || event.sourceId() == null || event.sourceId() <= 0
                || event.title() == null || event.title().isBlank()
                || event.title().codePointCount(0, event.title().length()) > 200
                || event.occurredAt() == null)
        {
            throw new NoticeValidationException("上游通知事件字段不完整或类型非法");
        }
        // TODO 通知表只有 title 列，contentSnapshot 当前仅接收/保留在事件契约中，
        // 不落库；若产品要求展示正文，需新增列并单独评审迁移。
    }

    private void validateMember(StudentAnnouncementMember member, Long bindingId)
    {
        if (member == null
                || member.userId() == null || member.userId() <= 0
                || member.bindingId() == null || member.bindingId() <= 0
                || member.teachingClassId() == null || member.teachingClassId() <= 0
                || member.joinedAt() == null
                || member.authority() == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
        if (!bindingId.equals(member.bindingId()))
        {
            // 跨绑定成员属于授权结果不一致，不能静默写入其他班。
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不一致");
        }
        if (member.leftAt() != null && !member.leftAt().isAfter(member.joinedAt()))
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
    }

    private int insertBatch(UpstreamNoticeEvent event, LocalDateTime visibleAt,
            LocalDateTime createdAt, List<Long> recipientUserIds)
    {
        try
        {
            // contentSnapshot 不写入：通知表没有正文列，详情由来源业务重新鉴权。
            return noticeMapper.insertUpstreamNotices(event.noticeKind(), event.bindingId(),
                    event.sourceId(), event.title(), visibleAt, createdAt, recipientUserIds);
        }
        catch (DataAccessException e)
        {
            // 不在本方法内自动重试：InnoDB lock wait timeout/deadlock 会回滚整个事务，
            // 当前 Spring 事务已不可继续；由上游在事件事务回滚后重新投递，
            // 唯一键保证重投幂等。自动重试需在上游投递层实现，不能在这里实现。
            throw new NoticePersistenceException("上游通知事件写入失败", e);
        }
    }
}
