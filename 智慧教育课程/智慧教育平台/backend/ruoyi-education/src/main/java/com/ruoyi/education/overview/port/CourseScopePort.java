package com.ruoyi.education.overview.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.ruoyi.education.overview.domain.CourseScope;

/**
 * 课程/教学班授权范围端口。
 *
 * <p>本阶段只定义契约，由第 2 组课程服务提供生产适配器；6-01 不直读课程表。
 * 所有方法必须批量查询，禁止按用户或班级逐个调用造成 N+1。</p>
 *
 * <p>返回 Map 必须包含所有请求 key；空 List 表示该用户没有授权班级；
 * 缺 key 视为契约违约，调用方映射为 UNAVAILABLE。</p>
 */
public interface CourseScopePort
{
    /**
     * 批量查询教师的授权教学班。
     *
     * @param teacherUserIds 教师用户 ID 集合，不能为空
     * @return 以教师用户 ID 为 key 的授权范围；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<CourseScope>> findTeacherScopes(Collection<Long> teacherUserIds);

    /**
     * 批量查询学生的有效在班范围。
     *
     * @param studentUserIds 学生用户 ID 集合，不能为空
     * @return 以学生用户 ID 为 key 的授权范围；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<CourseScope>> findStudentScopes(Collection<Long> studentUserIds);

    /**
     * 查询管理员授权组织下的教学班范围。
     *
     * @param organizationId 组织 ID，不能为空
     * @return 组织内教学班范围；无班级时返回空 List
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    List<CourseScope> findOrganizationScopes(Long organizationId);
}
