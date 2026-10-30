package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.StudentAnnouncementMember;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticePersistenceException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 公告发布 fan-out 服务。
 *
 * <p>在公告发布事务内查询绑定下的当前有效学生，按入班区间
 * {@code [joinedAt, leftAt)} 过滤公告发布时点，再批量写入
 * {@code ANNOUNCEMENT} 通知。生产学生授权适配器未接入时抛出 503，
 * 公告发布事务回滚，不会留下“已发布但无通知”的半成品。</p>
 */
@Service
public class AnnouncementNoticeFanoutService implements AnnouncementNoticeFanoutPort
{
    /**
     * 单批接收人数上限。批量插入可减少往返和锁持有时间，但过大的批量会
     * 放大与 read-all 当前读 gap lock 的等待范围，因此固定为 500。
     */
    private static final int BATCH_SIZE = 500;

    private final StudentAnnouncementAuthorizationPort authorizationPort;

    private final StudentNoticeMapper noticeMapper;

    /**
     * Spring 构造器。学生授权适配器是可选的：缺失时不阻止应用启动，
     * 但在实际 fan-out 时按 503 失败关闭。
     *
     * @param authorizationPortProvider 学生授权适配器提供者
     * @param noticeMapper 通知 Mapper
     */
    @Autowired
    public AnnouncementNoticeFanoutService(
            ObjectProvider<StudentAnnouncementAuthorizationPort> authorizationPortProvider,
            StudentNoticeMapper noticeMapper)
    {
        this(authorizationPortProvider.getIfAvailable(), noticeMapper);
    }

    AnnouncementNoticeFanoutService(StudentAnnouncementAuthorizationPort authorizationPort,
            StudentNoticeMapper noticeMapper)
    {
        this.authorizationPort = authorizationPort;
        this.noticeMapper = Objects.requireNonNull(noticeMapper, "noticeMapper");
    }

    /**
     * 为一次实际完成 {@code DRAFT -> PUBLISHED} 的公告生成学生通知。
     *
     * <p>调用方必须处于公告发布事务中；本方法使用 {@code MANDATORY} 传播，
     * 与公告状态变更同事务。重复触发依赖数据库唯一键幂等，不覆盖已有
     * {@code read_at}。单批最多 500 条，按接收人分批写入。</p>
     *
     * @param announcement 已发布公告，必须包含 ID、绑定、标题和 opensAt
     * @param publishedAt 公告发布动作的服务端 UTC 时间，用于入班区间判断
     * @return 数据库返回的处理行数；MySQL 默认驱动可能把重复键计为匹配行，
     *         幂等以通知行数不增和 readAt 不变为准
     * @throws NoticeAuthorizationUnavailableException 授权适配器缺失、异常、
     *         返回 null、跨绑定或字段不完整；映射为 503，公告发布回滚
     * @throws NoticePersistenceException 公告数据不完整或通知写入失败；映射为 500
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         调用方未开启事务时由 Spring 抛出
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int fanOutAnnouncement(CourseAnnouncement announcement, LocalDateTime publishedAt)
    {
        validateAnnouncement(announcement, publishedAt);
        LocalDateTime normalizedPublishedAt =
                DatabaseTimePrecision.toMillisecondPrecision(publishedAt);
        LocalDateTime visibleAt =
                DatabaseTimePrecision.toMillisecondPrecision(announcement.getOpensAt());

        List<StudentAnnouncementMember> members = loadMembers(
                announcement.getBindingId(), normalizedPublishedAt);
        Set<Long> recipients = selectRecipients(
                members, announcement.getBindingId(), normalizedPublishedAt);
        if (recipients.isEmpty())
        {
            return 0;
        }

        // 分批写入：单批不超过 500，降低与 read-all 当前读 gap lock 的冲突窗口。
        // 同事务内任一批失败即回滚公告发布和此前批次，不产生部分通知。
        List<Long> recipientIds = new ArrayList<>(recipients);
        int inserted = 0;
        for (int from = 0; from < recipientIds.size(); from += BATCH_SIZE)
        {
            int to = Math.min(from + BATCH_SIZE, recipientIds.size());
            inserted += insertBatch(announcement, visibleAt, normalizedPublishedAt,
                    recipientIds.subList(from, to));
        }
        return inserted;
    }

    /**
     * 查询绑定下当前有效学生。生产适配器未接入时失败关闭。
     */
    private List<StudentAnnouncementMember> loadMembers(Long bindingId, LocalDateTime at)
    {
        // TODO 生产学生授权适配器尚未接入；第 2 组提供绑定成员契约后解除。
        // 该分支必须保持 503 失败关闭，不能退回直接查询第 2 组私有表。
        if (authorizationPort == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权服务尚未接入");
        }

        List<StudentAnnouncementMember> members;
        try
        {
            members = authorizationPort.listActiveStudents(bindingId, at);
        }
        catch (RuntimeException e)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权查询失败", e);
        }
        if (members == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
        return members;
    }

    /**
     * 按入班区间过滤接收人。区间语义为 [joinedAt, leftAt)：
     * leftAt 为空表示未退班；发布时点等于 joinedAt 可接收，等于 leftAt 不可接收。
     */
    private Set<Long> selectRecipients(List<StudentAnnouncementMember> members,
            Long bindingId, LocalDateTime publishedAt)
    {
        Set<Long> recipients = new LinkedHashSet<>();
        for (StudentAnnouncementMember member : members)
        {
            validateMember(member, bindingId);
            if (!member.authority().active())
            {
                // 非当前成员/绑定或教学班非 ACTIVE：不属于本次 fan-out 接收人。
                continue;
            }

            LocalDateTime joinedAt = DatabaseTimePrecision.toMillisecondPrecision(member.joinedAt());
            LocalDateTime leftAt = DatabaseTimePrecision.toMillisecondPrecision(member.leftAt());
            if (publishedAt.isBefore(joinedAt))
            {
                // 发布时点早于入班时间：新入班学生不补发历史公告通知。
                continue;
            }
            if (leftAt != null && !publishedAt.isBefore(leftAt))
            {
                // 发布时点已到退班时间：退班学生不属于发布时点的在班成员。
                continue;
            }
            recipients.add(member.userId());
        }
        return recipients;
    }

    private void validateAnnouncement(CourseAnnouncement announcement, LocalDateTime publishedAt)
    {
        if (announcement == null
                || announcement.getId() == null || announcement.getId() <= 0
                || announcement.getBindingId() == null || announcement.getBindingId() <= 0
                || announcement.getTitle() == null || announcement.getTitle().isBlank()
                || announcement.getOpensAt() == null
                || publishedAt == null)
        {
            throw new NoticePersistenceException("公告 fan-out 数据不完整");
        }
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
            // 跨绑定成员属于授权结果不一致，不能静默过滤或写入其他班。
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不一致");
        }
        if (member.leftAt() != null && !member.leftAt().isAfter(member.joinedAt()))
        {
            throw new NoticeAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
    }

    private int insertBatch(CourseAnnouncement announcement, LocalDateTime visibleAt,
            LocalDateTime createdAt, List<Long> recipientUserIds)
    {
        try
        {
            return noticeMapper.insertAnnouncementNotices(announcement.getId(),
                    announcement.getBindingId(), announcement.getTitle(),
                    visibleAt, createdAt, recipientUserIds);
        }
        catch (DataAccessException e)
        {
            // 不在本方法内自动重试：InnoDB lock wait timeout/deadlock 会回滚整个事务，
            // 当前 Spring 事务已不可继续；由调用方在公告发布事务回滚后重试整个发布操作，
            // 唯一键保证重试幂等。自动重试需在入口层新开事务，不能在这里实现。
            throw new NoticePersistenceException("公告通知 fan-out 写入失败", e);
        }
    }
}
