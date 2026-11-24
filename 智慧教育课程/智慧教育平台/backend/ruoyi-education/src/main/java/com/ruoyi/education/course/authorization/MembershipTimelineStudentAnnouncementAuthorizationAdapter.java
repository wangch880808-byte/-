package com.ruoyi.education.course.authorization;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 基于 #39 {@link EduMembershipTimelineService} 的学生通知授权适配器。
 *
 * <p>本适配器只做时点成员快照到公告授权快照的映射，不直接查询成员、绑定或
 * 教学班表，也不缓存结果。{@code at} 只接受服务端时间：读取/已读路径使用
 * 服务端当前 UTC 时间，按班投递的上游事件使用事件 {@code occurredAt}；
 * 本类不接受客户端时间。</p>
 *
 * <p>教学班 status 没有生效/失效时间列，因此 {@code classOpen} 只使用 #39
 * 返回的<b>当前库值</b>，不按历史时点回溯；绑定和学生成员段则严格使用
 * #39 的半开区间 {@code [start, end)}。当前班级状态不参与“历史接收资格”
 * 的推断，调用方只把它作为当前状态的一部分。</p>
 *
 * <p>来源服务通过 {@link ObjectProvider} 可选注入：缺少来源时应用上下文仍可
 * 创建，但三个查询方法都会失败关闭，不返回正常空列表、假零或放行；有来源时
 * 本类仍是唯一的 {@link StudentAnnouncementAuthorizationPort} 生产实现。</p>
 *
 * <p>时间统一使用 UTC 与项目既有的 {@link DatabaseTimePrecision} 数据库毫秒规则：
 * 入参 {@code at}、返回的成员区间以及区间覆盖判断都先按毫秒归一化，避免传输或
 * 比较时残留纳秒精度。教学班状态仍取当前值，不随 {@code at} 变化。</p>
 *
 * <p>返回结果必须完整且内部一致：请求用户、绑定、教学班、课程空间、成员标识
 * 逐项核对；有效成员段必须覆盖本次 {@code at}；{@code active=true} 但
 * {@code bindingActive=false}、重复绑定、重复学生、冲突成员段等歧义一律抛异常
 * 失败关闭。空成员列表是正常结果；明确非成员的正常快照保留 inactive 语义，
 * 不因其没有入班窗口而误报授权不可用。</p>
 *
 * <p>所有异常均向上抛出；通知/公告服务会按既有规则映射为 503 失败关闭。</p>
 */
@Service
public class MembershipTimelineStudentAnnouncementAuthorizationAdapter
        implements StudentAnnouncementAuthorizationPort
{
    private final EduMembershipTimelineService timelineService;

    /**
     * Spring 构造器。来源服务是可选的：缺失时不阻止应用启动，业务调用失败关闭。
     *
     * @param timelineServiceProvider #39 时点成员查询服务提供者
     */
    @Autowired
    public MembershipTimelineStudentAnnouncementAuthorizationAdapter(
            ObjectProvider<EduMembershipTimelineService> timelineServiceProvider)
    {
        this(timelineServiceProvider.getIfAvailable());
    }

    /**
     * 测试用构造器。
     *
     * @param timelineService #39 时点成员查询服务；允许为 null，表示来源缺失，
     *        业务调用时按授权不可用失败关闭
     */
    MembershipTimelineStudentAnnouncementAuthorizationAdapter(
            EduMembershipTimelineService timelineService)
    {
        this.timelineService = timelineService;
    }

    /**
     * 解析指定绑定下学生在 {@code at} 时点的公告/通知授权快照。
     *
     * <p>返回快照的用户 ID 必须与请求用户一致；有效成员段必须覆盖本次
     * {@code at}。非成员返回 inactive 快照，不补造成员区间。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，正数
     * @param userId 学生用户 ID，正数
     * @param at 服务端 UTC 时间；读取/已读为当前时间，投递事件为 occurredAt
     * @return 绑定、成员段和授权快照；非成员返回 active=false
     * @throws IllegalArgumentException ID 或时间参数为空/非正数
     * @throws IllegalStateException 来源缺失、结果为空、不完整或内部不一致
     */
    @Override
    public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
    {
        requirePositive(bindingId, "bindingId");
        requirePositive(userId, "userId");
        Instant point = queryPoint(at);
        EduMembershipTimelineService source = requireTimelineService();
        EduStudentBindingAccess snapshot = source.studentInBinding(bindingId, userId, point);
        requireBindingSnapshot(snapshot, bindingId, userId, point);
        return toAccess(snapshot);
    }

    /**
     * 返回学生在 {@code at} 时点的全部有效绑定快照。
     *
     * <p>每行都必须属于请求用户且为有效绑定；同一绑定重复出现属于授权歧义，
     * 失败关闭，不去重或任选一条。</p>
     *
     * @param userId 学生用户 ID，正数
     * @param at 服务端 UTC 时间；读取/已读为当前时间
     * @return 有效绑定快照；无绑定返回空列表
     * @throws IllegalArgumentException 用户 ID 为空/非正数或时间为空
     * @throws IllegalStateException 来源缺失、结果为空、不完整、非活动或存在重复绑定
     */
    @Override
    public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
    {
        requirePositive(userId, "userId");
        Instant point = queryPoint(at);
        EduMembershipTimelineService source = requireTimelineService();
        List<EduStudentBindingAccess> snapshots = source.studentBindings(userId, point);
        if (snapshots == null)
        {
            throw new IllegalStateException("学生通知授权查询返回空结果对象");
        }

        List<StudentAnnouncementAccess> result = new ArrayList<>(snapshots.size());
        Set<Long> bindingIds = new HashSet<>();
        for (EduStudentBindingAccess snapshot : snapshots)
        {
            requireBindingSnapshot(snapshot, null, userId, point);
            if (!snapshot.active())
            {
                throw new IllegalStateException("学生通知授权结果包含非活动绑定");
            }
            if (!bindingIds.add(snapshot.bindingId()))
            {
                throw new IllegalStateException("学生通知授权结果存在重复绑定");
            }
            result.add(toAccess(snapshot));
        }
        return List.copyOf(result);
    }

    /**
     * 返回指定绑定下 {@code at} 时点在班的学生成员段，用于公告 fan-out。
     *
     * <p>#39 的成员查询不返回教学班状态，因此本方法在非空结果上额外做一次
     * {@code studentInBinding} 查询读取当前 {@code classOpen}，再统一应用到全部
     * 成员；不按成员逐个查询，避免 N+1。该补查必须与首位成员的用户、绑定、
     * 教学班、active/bindingActive 和成员区间一致；补查返回 inactive、其他用户
     * 或不同成员段时失败关闭，不能把补查结果改写成有效成员。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，正数
     * @param at 服务端 UTC 时间；读取/已读为当前时间，投递事件为 occurredAt
     * @return 在班学生成员段；无成员返回空列表
     * @throws IllegalArgumentException 绑定 ID 为空/非正数或时间为空
     * @throws IllegalStateException 来源缺失、结果为空、不完整、重复或补查不一致
     */
    @Override
    public List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at)
    {
        requirePositive(bindingId, "bindingId");
        Instant point = queryPoint(at);
        EduMembershipTimelineService source = requireTimelineService();
        List<EduStudentMemberSegment> members = source.studentsInBinding(bindingId, point);
        if (members == null)
        {
            throw new IllegalStateException("学生教学班授权查询返回空结果对象");
        }
        if (members.isEmpty())
        {
            return List.of();
        }

        Set<Long> memberIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (EduStudentMemberSegment member : members)
        {
            requireMember(member, point);
            if (!memberIds.add(member.memberId()))
            {
                throw new IllegalStateException("学生教学班授权结果存在重复成员记录");
            }
            if (!userIds.add(member.userId()))
            {
                throw new IllegalStateException("学生教学班授权结果存在重复学生");
            }
        }

        EduStudentMemberSegment first = members.get(0);
        EduStudentBindingAccess classSnapshot = source.studentInBinding(bindingId,
                first.userId(), point);
        requireBindingSnapshot(classSnapshot, bindingId, first.userId(), point);
        if (!classSnapshot.active() || !classSnapshot.bindingActive())
        {
            throw new IllegalStateException("学生成员查询与成员状态不一致");
        }
        if (!Objects.equals(classSnapshot.teachingClassId(), first.teachingClassId()))
        {
            throw new IllegalStateException("学生成员查询与教学班不一致");
        }
        if (!sameInstant(classSnapshot.joinedAt(), first.joinedAt())
                || !sameInstant(classSnapshot.leftAt(), first.leftAt()))
        {
            throw new IllegalStateException("学生成员查询与成员段不一致");
        }

        List<StudentAnnouncementMember> result = new ArrayList<>(members.size());
        for (EduStudentMemberSegment member : members)
        {
            if (!Objects.equals(classSnapshot.teachingClassId(), member.teachingClassId()))
            {
                throw new IllegalStateException("学生成员查询与教学班不一致");
            }
            result.add(new StudentAnnouncementMember(member.userId(), bindingId,
                    member.teachingClassId(), toLocal(member.joinedAt()),
                    toLocal(member.leftAt()),
                    authority(classSnapshot.active(), classSnapshot.bindingActive(),
                            classSnapshot.classOpen())));
        }
        return List.copyOf(result);
    }

    /**
     * 将服务端 LocalDateTime 按项目数据库毫秒规则归一化后转成 UTC Instant。
     *
     * @param at 服务端 UTC 时间
     * @return UTC、毫秒精度的 Instant
     * @throws IllegalArgumentException at 为空
     */
    private static Instant queryPoint(LocalDateTime at)
    {
        if (at == null)
        {
            throw new IllegalArgumentException("查询时点不能为空");
        }
        return DatabaseTimePrecision.toMillisecondPrecision(at).toInstant(ZoneOffset.UTC);
    }

    /**
     * 将 Instant 按项目数据库毫秒规则转成 UTC LocalDateTime；null 保持为无上界。
     *
     * @param instant UTC 时间
     * @return UTC、毫秒精度的 LocalDateTime 或 null
     */
    private static LocalDateTime toLocal(Instant instant)
    {
        if (instant == null)
        {
            return null;
        }
        return DatabaseTimePrecision.toMillisecondPrecision(
                LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /**
     * 将 Instant 归一化为 UTC 毫秒精度，用于返回与比较。
     *
     * @param instant UTC 时间，可为 null
     * @return UTC、毫秒精度的 Instant 或 null
     */
    private static Instant toMillis(Instant instant)
    {
        if (instant == null)
        {
            return null;
        }
        return DatabaseTimePrecision.toMillisecondPrecision(
                LocalDateTime.ofInstant(instant, ZoneOffset.UTC)).toInstant(ZoneOffset.UTC);
    }

    /**
     * 判断两个 Instant 在数据库毫秒精度下是否相等。
     *
     * @param left 左侧时间，可为 null
     * @param right 右侧时间，可为 null
     * @return 归一化到毫秒后是否相等
     */
    private static boolean sameInstant(Instant left, Instant right)
    {
        return Objects.equals(toMillis(left), toMillis(right));
    }

    /**
     * 将绑定快照映射为公告授权快照。
     *
     * @param snapshot 已通过完整性校验的 #39 快照
     * @return 公告授权快照
     */
    private static StudentAnnouncementAccess toAccess(EduStudentBindingAccess snapshot)
    {
        return new StudentAnnouncementAccess(snapshot.bindingId(), snapshot.teachingClassId(),
                toLocal(snapshot.joinedAt()), toLocal(snapshot.leftAt()),
                authority(snapshot.active(), snapshot.bindingActive(), snapshot.classOpen()));
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
     * 校验绑定快照完整、用户匹配、区间覆盖本次 at 且状态组合不矛盾。
     *
     * @param snapshot #39 快照
     * @param expectedBindingId 请求绑定 ID；null 表示逐行校验
     * @param expectedUserId 请求用户 ID；null 表示不核对用户
     * @param at 已归一化为毫秒的查询时点
     */
    private static void requireBindingSnapshot(EduStudentBindingAccess snapshot,
            Long expectedBindingId, Long expectedUserId, Instant at)
    {
        if (snapshot == null || !isPositive(snapshot.bindingId())
                || !isPositive(snapshot.teachingClassId())
                || !isPositive(snapshot.courseSpaceId()) || !isPositive(snapshot.userId()))
        {
            throw new IllegalStateException("学生通知授权结果不完整");
        }
        if (expectedBindingId != null && !expectedBindingId.equals(snapshot.bindingId()))
        {
            throw new IllegalStateException("学生通知授权结果绑定不一致");
        }
        if (expectedUserId != null && !expectedUserId.equals(snapshot.userId()))
        {
            throw new IllegalStateException("学生通知授权结果用户不一致");
        }
        if (snapshot.active())
        {
            if (!snapshot.bindingActive())
            {
                throw new IllegalStateException("学生通知授权结果绑定状态矛盾");
            }
            requireWindowCovers(snapshot.joinedAt(), snapshot.leftAt(), at);
        }
        else if (snapshot.joinedAt() != null || snapshot.leftAt() != null)
        {
            throw new IllegalStateException("学生通知授权结果成员区间矛盾");
        }
    }

    /**
     * 校验成员快照完整且成员段覆盖本次 at。
     *
     * <p>#39 的成员段 record 不带 bindingId；绑定一致性由本次按 bindingId
     * 查询的 SQL 保证，适配器校验成员标识、教学班、用户和区间。</p>
     *
     * @param member #39 成员段
     * @param at 已归一化为毫秒的查询时点
     */
    private static void requireMember(EduStudentMemberSegment member, Instant at)
    {
        if (member == null || !isPositive(member.memberId()) || !isPositive(member.userId())
                || !isPositive(member.teachingClassId()))
        {
            throw new IllegalStateException("学生教学班授权结果不完整");
        }
        requireWindowCovers(member.joinedAt(), member.leftAt(), at);
    }

    /**
     * 校验半开成员区间 {@code [joinedAt, leftAt)} 覆盖本次 at。
     *
     * <p>入班时间必须不晚于 at；退班时间为空或必须晚于 at；退班时间还必须
     * 晚于入班时间。所有比较先统一到数据库毫秒精度，避免纳秒尾差改变边界。</p>
     *
     * @param joinedAt 入班时间
     * @param leftAt 退班时间，可为 null
     * @param at 已归一化为毫秒的查询时点
     */
    private static void requireWindowCovers(Instant joinedAt, Instant leftAt, Instant at)
    {
        if (joinedAt == null)
        {
            throw new IllegalStateException("学生通知授权结果缺少入班时间");
        }
        Instant joined = toMillis(joinedAt);
        Instant left = toMillis(leftAt);
        Instant point = toMillis(at);
        if (left != null && !left.isAfter(joined))
        {
            throw new IllegalStateException("学生通知授权结果入退班时间不合法");
        }
        if (joined.isAfter(point))
        {
            throw new IllegalStateException("学生通知授权成员段未覆盖查询时点");
        }
        if (left != null && !point.isBefore(left))
        {
            throw new IllegalStateException("学生通知授权成员段已结束于查询时点");
        }
    }

    /**
     * 返回可用的来源服务；来源缺失时失败关闭。
     *
     * @return #39 时点成员查询服务
     * @throws IllegalStateException 来源服务未注入
     */
    private EduMembershipTimelineService requireTimelineService()
    {
        if (timelineService == null)
        {
            throw new IllegalStateException("学生通知授权来源服务尚未接入");
        }
        return timelineService;
    }

    /**
     * 判断 ID 是否为正数。
     *
     * @param value ID
     * @return 非空且大于 0
     */
    private static boolean isPositive(Long value)
    {
        return value != null && value > 0;
    }

    /**
     * 校验端口要求的正数 ID。
     *
     * @param value ID
     * @param name 字段名
     */
    private static void requirePositive(Long value, String name)
    {
        if (!isPositive(value))
        {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
    }
}
