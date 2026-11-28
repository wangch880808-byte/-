package com.ruoyi.education.overview.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.overview.domain.CourseScope;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.exception.OverviewValidationException;
import com.ruoyi.education.overview.port.CourseScopePort;
import com.ruoyi.education.security.EduActor;

/**
 * 课程/教学班授权范围的生产适配。
 *
 * <p>只消费第 2 组公开的 {@link EduCourseAccessService} 和
 * {@link EduMembershipTimelineService}，不直读课程、教学班或成员表，也不复制
 * 撤权规则。授权判定以来源服务每次查询的当前库结果为准。</p>
 *
 * <p>身份：端口参数来自 6-01 聚合层，但适配器仍在源线程复核“请求的用户 ID
 * 必须是当前登录人”，防止内部调用方误传他人 ID 读取课程范围。源线程的
 * 认证上下文由 {@code OverviewConfiguration} 使用 Spring Security 的
 * {@code DelegatingSecurityContextExecutor} 传播真实上下文，不伪造
 * SecurityContext。</p>
 *
 * <p>人数口径：第 2 组公开契约没有批量人数汇总方法，学生数按教学班调用
 * {@link EduCourseAccessService#activeMembers(Long)} 取当前有效成员数；这是
 * 不直读成员表且保持来源授权语义的现有契约路径。待第 2 组补充批量人数契约后
 * 再收敛为单次查询，不在此处自行 join 课程表或成员表。</p>
 */
@Component
public class CourseScopePortAdapter implements CourseScopePort
{
    private final EduCourseAccessService courseAccess;

    private final EduMembershipTimelineService membershipTimeline;

    private final Clock clock;

    /**
     * 创建课程范围适配器。
     *
     * @param courseAccess       第 2 组课程只读契约
     * @param membershipTimeline 第 2 组成员时点契约，用于学生有效绑定
     * @param clock              服务端 UTC 时钟
     */
    @Autowired
    public CourseScopePortAdapter(EduCourseAccessService courseAccess,
            EduMembershipTimelineService membershipTimeline,
            @Qualifier("overviewClock") Clock clock)
    {
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.membershipTimeline = Objects.requireNonNull(membershipTimeline, "membershipTimeline");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 批量查询教师授权教学班。当前 6-01 每次只请求当前登录教师，适配器仍按
     * 端口约定返回覆盖全部请求 key 的 Map。
     *
     * @param teacherUserIds 教师用户 ID 集合，不能为空
     * @return 以教师 ID 为 key 的授权范围
     */
    @Override
    public Map<Long, List<CourseScope>> findTeacherScopes(Collection<Long> teacherUserIds)
    {
        Long currentUserId = requireCurrentUser(teacherUserIds, "教师");
        if (!EduActor.assigned("teacher"))
        {
            throw new OverviewAccessDeniedException("当前账号没有教师身份");
        }

        List<EduClassRef> classes = courseAccess.classesOfTeacher(currentUserId);
        if (classes == null)
        {
            throw new IllegalStateException("教师教学班契约返回 null");
        }

        List<CourseScope> scopes = new ArrayList<>();
        for (EduClassRef teachingClass : classes)
        {
            if (teachingClass == null || teachingClass.classId() == null)
            {
                throw new IllegalStateException("教师教学班契约返回不完整记录");
            }
            long studentCount = activeMemberCount(teachingClass.classId());
            List<EduClassBindingRef> bindings = courseAccess.activeBindings(teachingClass.classId());
            if (bindings == null)
            {
                throw new IllegalStateException("教学班绑定契约返回 null");
            }
            if (bindings.isEmpty())
            {
                // 未绑定课程的教学班仍属于教师的授权范围；class.count 需要计入，
                // 但没有 bindingId 时其他课堂/作业/成绩来源不会对它取数。
                scopes.add(new CourseScope(teachingClass.classId(), null, null,
                        teachingClass.organizationId(), teachingClass.name(), null, studentCount));
                continue;
            }
            for (EduClassBindingRef binding : bindings)
            {
                if (binding == null || binding.bindingId() == null
                        || !teachingClass.classId().equals(binding.classId()))
                {
                    throw new IllegalStateException("教学班绑定契约返回不完整或跨班记录");
                }
                scopes.add(new CourseScope(teachingClass.classId(), binding.bindingId(),
                        binding.courseSpaceId(), teachingClass.organizationId(),
                        teachingClass.name(), null, studentCount));
            }
        }
        return Map.of(currentUserId, List.copyOf(scopes));
    }

    /**
     * 批量查询学生有效在班范围。学生成员时点统一使用服务端当前 UTC 时间，
     * 教学班/绑定状态由第 2 组契约按同一时点判定。
     *
     * @param studentUserIds 学生用户 ID 集合，不能为空
     * @return 以学生 ID 为 key 的授权范围
     */
    @Override
    public Map<Long, List<CourseScope>> findStudentScopes(Collection<Long> studentUserIds)
    {
        Long currentUserId = requireCurrentUser(studentUserIds, "学生");
        if (!EduActor.assigned("student"))
        {
            throw new OverviewAccessDeniedException("当前账号没有学生身份");
        }

        List<EduClassRef> classes = courseAccess.classesOfStudent(currentUserId);
        if (classes == null)
        {
            throw new IllegalStateException("学生教学班契约返回 null");
        }
        Instant at = clock.instant();
        List<EduStudentBindingAccess> bindings = membershipTimeline.studentBindings(currentUserId, at);
        if (bindings == null)
        {
            throw new IllegalStateException("学生绑定契约返回 null");
        }
        Map<Long, List<EduStudentBindingAccess>> bindingsByClass = bindings.stream()
                .filter(Objects::nonNull)
                .filter(EduStudentBindingAccess::bindingActive)
                .filter(EduStudentBindingAccess::active)
                .filter(EduStudentBindingAccess::classOpen)
                .collect(Collectors.groupingBy(EduStudentBindingAccess::teachingClassId,
                        LinkedHashMap::new, Collectors.toList()));

        List<CourseScope> scopes = new ArrayList<>();
        for (EduClassRef teachingClass : classes)
        {
            if (teachingClass == null || teachingClass.classId() == null)
            {
                throw new IllegalStateException("学生教学班契约返回不完整记录");
            }
            List<EduStudentBindingAccess> classBindings = bindingsByClass.get(teachingClass.classId());
            if (classBindings == null || classBindings.isEmpty())
            {
                // 学生仍在有效教学班但当前没有课程绑定时，班级数仍应计入；
                // 学生总览不使用 studentCount，保持 null 表示来源未提供。
                scopes.add(new CourseScope(teachingClass.classId(), null, null,
                        teachingClass.organizationId(), teachingClass.name(), null, null));
                continue;
            }
            for (EduStudentBindingAccess binding : classBindings)
            {
                if (binding.bindingId() == null)
                {
                    throw new IllegalStateException("学生绑定契约缺少 bindingId");
                }
                scopes.add(new CourseScope(teachingClass.classId(), binding.bindingId(),
                        binding.courseSpaceId(), teachingClass.organizationId(),
                        teachingClass.name(), null, null));
            }
        }
        return Map.of(currentUserId, List.copyOf(scopes));
    }

    /**
     * 管理员组织范围来源尚未接入；不直读组织表或用其他班级近似。
     *
     * @param organizationId 组织 ID
     * @return 不会返回；当前来源不可用
     * @throws UnsupportedOperationException 当前阶段未接入管理员组织范围
     */
    @Override
    public List<CourseScope> findOrganizationScopes(Long organizationId)
    {
        if (organizationId == null || organizationId <= 0L)
        {
            throw new OverviewValidationException("organizationId 必须是正整数");
        }
        throw new UnsupportedOperationException("管理员组织范围来源尚未接通");
    }

    private long activeMemberCount(Long classId)
    {
        List<?> members = courseAccess.activeMembers(classId);
        if (members == null)
        {
            throw new IllegalStateException("教学班成员契约返回 null");
        }
        return members.size();
    }

    /**
     * 校验请求 key 与当前认证身份一致，并返回当前用户 ID。
     *
     * @param userIds 端口请求的用户 ID 集合
     * @param role    审计文案使用的角色名称
     * @return 当前登录用户 ID
     */
    private Long requireCurrentUser(Collection<Long> userIds, String role)
    {
        if (userIds == null || userIds.isEmpty())
        {
            throw new OverviewValidationException(role + "用户 ID 集合不能为空");
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null)
        {
            throw new OverviewAccessDeniedException("总览缺少可信登录身份");
        }
        for (Long userId : userIds)
        {
            if (userId == null || userId <= 0L)
            {
                throw new OverviewValidationException(role + "用户 ID 必须是正整数");
            }
            if (!currentUserId.equals(userId))
            {
                throw new OverviewAccessDeniedException("总览只能查询当前登录人的课程范围");
            }
        }
        return currentUserId;
    }
}
