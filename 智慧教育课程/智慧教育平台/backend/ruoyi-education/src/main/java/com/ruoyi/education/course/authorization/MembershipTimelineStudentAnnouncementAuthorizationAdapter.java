package com.ruoyi.education.course.authorization;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;

/**
 * 基于 #39 {@link EduMembershipTimelineService} 的学生通知授权适配器。
 *
 * <p>本适配器只做时点成员快照到公告授权快照的映射，不直接查询成员、绑定或
 * 教学班表，也不缓存结果。{@code at} 必须在调用方来自服务端当前时间或上游
 * 事件 {@code occurredAt}，本类不接受客户端时间。</p>
 *
 * <p>教学班 status 没有生效/失效时间列，因此 {@code classOpen} 只使用 #39
 * 返回的<b>当前库值</b>，不按历史时点回溯；绑定和学生成员段则严格使用
 * #39 的半开区间 {@code [start, end)}。当前班级状态不参与“历史接收资格”
 * 的推断，调用方只把它作为当前状态的一部分。</p>
 *
 * <p>所有异常均向上抛出；通知/公告服务会按既有规则映射为 503 失败关闭。
 * 空成员列表是正常结果；不完整、跨绑定或无法唯一判定的快照不猜测。</p>
 */
@Service
public class MembershipTimelineStudentAnnouncementAuthorizationAdapter
        implements StudentAnnouncementAuthorizationPort
{
    private final EduMembershipTimelineService timelineService;

    /**
     * 创建适配器。
     *
     * @param timelineService #39 时点成员查询服务
     */
    public MembershipTimelineStudentAnnouncementAuthorizationAdapter(
            EduMembershipTimelineService timelineService)
    {
        this.timelineService = Objects.requireNonNull(timelineService, "timelineService");
    }

    /**
     * 解析指定绑定下学生在 {@code at} 时点的公告/通知授权快照。
     *
     * @param bindingId 课程—教学班绑定 ID，正数
     * @param userId 学生用户 ID，正数
     * @param at 服务端 UTC 时间
     * @return 绑定、成员段和授权快照；非成员返回 active=false
     */
    @Override
    public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
    {
        requirePositive(bindingId, "bindingId");
        requirePositive(userId, "userId");
        Instant point = toInstant(at);
        EduStudentBindingAccess snapshot = timelineService.studentInBinding(bindingId, userId,
                point);
        requireBindingSnapshot(snapshot, bindingId);
        if (snapshot.active())
        {
            requireWindow(snapshot.joinedAt(), snapshot.leftAt());
        }
        return new StudentAnnouncementAccess(snapshot.bindingId(), snapshot.teachingClassId(),
                toLocal(snapshot.joinedAt()), toLocal(snapshot.leftAt()),
                authority(snapshot.active(), snapshot.bindingActive(), snapshot.classOpen()));
    }

    /**
     * 返回学生在 {@code at} 时点的全部有效绑定快照。
     *
     * @param userId 学生用户 ID，正数
     * @param at 服务端 UTC 时间
     * @return 有效绑定快照；无绑定返回空列表
     */
    @Override
    public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
    {
        requirePositive(userId, "userId");
        Instant point = toInstant(at);
        List<EduStudentBindingAccess> snapshots = timelineService.studentBindings(userId, point);
        if (snapshots == null)
        {
            throw new IllegalStateException("学生通知授权查询返回空结果对象");
        }
        List<StudentAnnouncementAccess> result = new ArrayList<>(snapshots.size());
        for (EduStudentBindingAccess snapshot : snapshots)
        {
            requireBindingSnapshot(snapshot, null);
            if (snapshot.joinedAt() == null)
            {
                throw new IllegalStateException("学生通知授权结果缺少入班时间");
            }
            requireWindow(snapshot.joinedAt(), snapshot.leftAt());
            result.add(new StudentAnnouncementAccess(snapshot.bindingId(),
                    snapshot.teachingClassId(), toLocal(snapshot.joinedAt()),
                    toLocal(snapshot.leftAt()),
                    authority(snapshot.active(), snapshot.bindingActive(), snapshot.classOpen())));
        }
        return List.copyOf(result);
    }

    /**
     * 返回指定绑定下 {@code at} 时点在班的学生成员段，用于公告 fan-out。
     *
     * <p>#39 的成员查询不返回教学班状态，因此本方法在非空结果上额外做一次
     * {@code studentInBinding} 查询读取当前 {@code classOpen}，再统一应用到全部
     * 成员；不按成员逐个查询，避免 N+1。班级状态仍是当前库值，不作历史回溯。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，正数
     * @param at 服务端 UTC 时间
     * @return 在班学生成员段；无成员返回空列表
     */
    @Override
    public List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at)
    {
        requirePositive(bindingId, "bindingId");
        Instant point = toInstant(at);
        List<EduStudentMemberSegment> members = timelineService.studentsInBinding(bindingId, point);
        if (members == null)
        {
            throw new IllegalStateException("学生教学班授权查询返回空结果对象");
        }
        if (members.isEmpty())
        {
            return List.of();
        }

        EduStudentMemberSegment first = members.get(0);
        requireMember(first);
        EduStudentBindingAccess classSnapshot = timelineService.studentInBinding(bindingId,
                first.userId(), point);
        requireBindingSnapshot(classSnapshot, bindingId);
        if (!classSnapshot.bindingActive())
        {
            throw new IllegalStateException("学生成员查询与绑定状态不一致");
        }
        if (!Objects.equals(classSnapshot.teachingClassId(), first.teachingClassId()))
        {
            throw new IllegalStateException("学生成员查询与教学班不一致");
        }

        List<StudentAnnouncementMember> result = new ArrayList<>(members.size());
        for (EduStudentMemberSegment member : members)
        {
            requireMember(member);
            if (!Objects.equals(classSnapshot.teachingClassId(), member.teachingClassId()))
            {
                throw new IllegalStateException("学生成员查询与教学班不一致");
            }
            result.add(new StudentAnnouncementMember(member.userId(), bindingId,
                    member.teachingClassId(), toLocal(member.joinedAt()),
                    toLocal(member.leftAt()),
                    authority(true, true, classSnapshot.classOpen())));
        }
        return List.copyOf(result);
    }

    /**
     * 将服务端 LocalDateTime 按 UTC 转为 #39 使用的 Instant。
     *
     * @param at 服务端 UTC 时间
     * @return UTC Instant；at 为空时抛异常
     */
    private static Instant toInstant(LocalDateTime at)
    {
        if (at == null)
        {
            throw new IllegalArgumentException("查询时点不能为空");
        }
        return at.toInstant(ZoneOffset.UTC);
    }

    /**
     * 将 #39 的 UTC Instant 转为 LocalDateTime；null 保持为无上界。
     *
     * @param instant UTC 时间
     * @return UTC LocalDateTime 或 null
     */
    private static LocalDateTime toLocal(Instant instant)
    {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * 构造授权快照。currentStudent 使用 #39 的 active，避免绑定失效时放行。
     *
     * @param currentStudent 时点成员是否有效
     * @param bindingActive 绑定在时点是否有效
     * @param teachingClassActive 教学班当前状态是否 ACTIVE
     * @return 授权快照
     */
    private static StudentAnnouncementAuthority authority(boolean currentStudent,
            boolean bindingActive, boolean teachingClassActive)
    {
        return new StudentAnnouncementAuthority(currentStudent, bindingActive, teachingClassActive);
    }

    /**
     * 校验绑定快照完整且没有跨绑定。
     *
     * @param snapshot #39 快照
     * @param expectedBindingId 请求绑定 ID；null 表示逐行校验
     */
    private static void requireBindingSnapshot(EduStudentBindingAccess snapshot,
            Long expectedBindingId)
    {
        if (snapshot == null || snapshot.bindingId() == null || snapshot.bindingId() <= 0
                || snapshot.teachingClassId() == null || snapshot.teachingClassId() <= 0)
        {
            throw new IllegalStateException("学生通知授权结果不完整");
        }
        if (expectedBindingId != null && !expectedBindingId.equals(snapshot.bindingId()))
        {
            throw new IllegalStateException("学生通知授权结果绑定不一致");
        }
    }

    /**
     * 校验成员快照完整。
     *
     * <p>#39 的成员段 record 不带 bindingId；绑定一致性由本次按 bindingId
     * 查询的 SQL 保证，适配器只校验成员本身字段。</p>
     *
     * @param member #39 成员段
     */
    private static void requireMember(EduStudentMemberSegment member)
    {
        if (member == null || member.userId() == null || member.userId() <= 0
                || member.teachingClassId() == null || member.teachingClassId() <= 0
                || member.joinedAt() == null)
        {
            throw new IllegalStateException("学生教学班授权结果不完整");
        }
        requireWindow(member.joinedAt(), member.leftAt());
    }

    /**
     * 校验半开成员区间；退班时间必须晚于入班时间。
     *
     * @param joinedAt 入班时间
     * @param leftAt 退班时间，可为 null
     */
    private static void requireWindow(Instant joinedAt, Instant leftAt)
    {
        if (joinedAt == null)
        {
            throw new IllegalStateException("学生通知授权结果缺少入班时间");
        }
        if (leftAt != null && !leftAt.isAfter(joinedAt))
        {
            throw new IllegalStateException("学生通知授权结果入退班时间不合法");
        }
    }

    /**
     * 校验端口要求的正数 ID。
     *
     * @param value ID
     * @param name 字段名
     */
    private static void requirePositive(Long value, String name)
    {
        if (value == null || value <= 0)
        {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
    }
}
