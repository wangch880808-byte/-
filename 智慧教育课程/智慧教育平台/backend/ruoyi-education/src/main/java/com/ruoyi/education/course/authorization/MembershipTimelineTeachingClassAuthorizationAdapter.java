package com.ruoyi.education.course.authorization;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduTeacherBindingAccess;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 教师公告授权的生产适配器：把第 2 组 {@link EduMembershipTimelineService}
 * 的公开任课快照转换为 {@link TeacherAnnouncementAccess}。
 *
 * <p>边界：本类只调用 {@link EduMembershipTimelineService#teacherInBinding}，
 * 不访问第 2 组 Mapper、私有表，也不扫描教师的全部班级；不新增 HTTP 端点。
 * 绑定、任课段和发布权限均由公开契约在传入的服务端 {@code at} 上判定。</p>
 *
 * <p>装配：来源通过 {@link ObjectProvider} 可选注入。缺少来源时本 Bean 仍可创建，
 * 但调用 {@link #resolve} 会失败关闭，由公告服务沿现有授权不可用链映射为 503，
 * 不能返回假授权。生产上下文中有来源时只注册本适配器一个
 * {@link TeachingClassAuthorizationPort} 实现。</p>
 *
 * <p>时间：{@code at} 视为服务端 UTC 墙钟时间，先按 {@link DatabaseTimePrecision}
 * 毫秒规则取整，再显式按 {@link ZoneOffset#UTC} 转换为 {@link Instant}；
 * 本类不另取当前时间，也不接受客户端时间。</p>
 */
@Component
public class MembershipTimelineTeachingClassAuthorizationAdapter
        implements TeachingClassAuthorizationPort
{
    private final ObjectProvider<EduMembershipTimelineService> timelineServiceProvider;

    /**
     * Spring 构造器。
     *
     * @param timelineServiceProvider 第 2 组任课时间线契约的可选提供者
     */
    public MembershipTimelineTeachingClassAuthorizationAdapter(
            ObjectProvider<EduMembershipTimelineService> timelineServiceProvider)
    {
        this.timelineServiceProvider = timelineServiceProvider;
    }

    /**
     * 解析教师在指定绑定下的公告授权。
     *
     * <p>合法拒绝（非任课教师、绑定已结束、教学班已关闭）保留为快照标志，
     * 由公告服务按现有规则返回 403；只有来源缺失、查询异常或结果为空、
     * 不完整、矛盾时才抛运行时异常，由公告服务映射为 503。</p>
     *
     * <p>{@code currentTeacher} 与 {@code bindingActive} 按契约独立保留，
     * 不照搬学生授权中“成员关系蕴含绑定有效”的写法；发布权限只取契约
     * {@code canPublish}，不按 {@code teacherRole}、管理员或课程负责人身份自行赋权。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，必须为正数
     * @param userId 服务端确认的教师用户 ID，必须为正数
     * @param at 服务端 UTC 时间，不能为空
     * @return 绑定、教学班和教师公告权限快照；非任课教师也返回快照，不伪造成 503
     * @throws IllegalArgumentException 输入标识或时点非法
     * @throws IllegalStateException 来源缺失、结果为空或结果不完整/矛盾
     * @throws RuntimeException 来源查询异常（数据库故障、重叠记录等）原样抛出
     */
    @Override
    public TeacherAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
    {
        requirePositiveInput(bindingId, "bindingId");
        requirePositiveInput(userId, "userId");
        if (at == null)
        {
            throw new IllegalArgumentException("at 不能为空");
        }
        Instant instantAt = DatabaseTimePrecision.toMillisecondPrecision(at)
                .toInstant(ZoneOffset.UTC);

        EduMembershipTimelineService timelineService = timelineServiceProvider.getIfAvailable();
        if (timelineService == null)
        {
            throw new IllegalStateException("教师任课时间线服务未接入，无法判定公告授权");
        }

        EduTeacherBindingAccess access = timelineService.teacherInBinding(bindingId, userId, instantAt);
        return toAccess(bindingId, userId, instantAt, access);
    }

    /**
     * 校验来源快照并转换。任何空值、跨用户/跨绑定、非法标识、错误区间或
     * 权限组合矛盾都失败关闭，不猜测为默认授权。
     */
    private TeacherAnnouncementAccess toAccess(Long bindingId, Long userId, Instant at,
            EduTeacherBindingAccess access)
    {
        if (access == null)
        {
            throw new IllegalStateException("教师任课时间线返回空授权结果");
        }
        if (!bindingId.equals(access.bindingId()) || !userId.equals(access.userId()))
        {
            throw new IllegalStateException("教师任课时间线返回的绑定或用户与请求不一致");
        }
        requirePositiveResult(access.bindingId(), "bindingId");
        requirePositiveResult(access.userId(), "userId");
        requirePositiveResult(access.teachingClassId(), "teachingClassId");
        requirePositiveResult(access.courseSpaceId(), "courseSpaceId");

        Instant publishedAt = access.bindingPublishedAt();
        Instant endedAt = access.bindingEndedAt();
        if (publishedAt == null)
        {
            throw new IllegalStateException("教师任课时间线缺少绑定发布时间");
        }
        if (endedAt != null && !endedAt.isAfter(publishedAt))
        {
            throw new IllegalStateException("教师任课时间线返回的绑定结束时间不晚于发布时间");
        }
        // 半开区间 [publishedAt, endedAt)：适配器独立复核来源标志，避免时间与标志矛盾时放行或误判。
        boolean expectedBindingActive = !publishedAt.isAfter(at)
                && (endedAt == null || endedAt.isAfter(at));
        if (expectedBindingActive != access.bindingActive())
        {
            throw new IllegalStateException("教师任课时间线的 bindingActive 与绑定区间矛盾");
        }

        Boolean canPublish = access.canPublish();
        if (access.currentTeacher())
        {
            // 任课教师缺少发布权限位属于不完整结果，不能降级为受限教师放行。
            if (canPublish == null)
            {
                throw new IllegalStateException("任课教师缺少发布权限标志");
            }
        }
        else if (canPublish != null)
        {
            // 契约规定非任课教师的 canPublish 为 null；非 null 视为矛盾结果。
            throw new IllegalStateException("非任课教师返回了发布权限标志");
        }

        // 发布权限只取契约值：团队 fallback 为 false，直接任课的 CO_TEACHER 可能为 true。
        // 非任课教师的 null 归一为 false，由公告服务按现有规则拒绝，不能误判为 503。
        boolean publishAllowed = Boolean.TRUE.equals(canPublish);
        // bindingActive 与 currentTeacher 独立保留：课程绑定结束不抹除教师身份标志。
        TeacherAnnouncementAuthority authority = new TeacherAnnouncementAuthority(
                access.currentTeacher(), publishAllowed,
                access.bindingActive(), access.classOpen());
        return new TeacherAnnouncementAccess(access.bindingId(), access.teachingClassId(), authority);
    }

    private static void requirePositiveInput(Long value, String field)
    {
        if (value == null || value <= 0)
        {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private static void requirePositiveResult(Long value, String field)
    {
        if (value == null || value <= 0)
        {
            throw new IllegalStateException("教师任课时间线返回的 " + field + " 必须为正数");
        }
    }
}
