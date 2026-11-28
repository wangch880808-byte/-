package com.ruoyi.education.overview.domain;

import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 教师或学生的授权教学班范围。
 *
 * <p>该对象只承载聚合所需的班级/绑定标识，不包含学生名单或其他组私有明细。
 * 名称字段可能为空；{@code studentCount} 为空表示适配器未提供该汇总，
 * 调用方应映射为 NO_DATA，不能当成 0。</p>
 *
 * @param teachingClassId 教学班 ID
 * @param bindingId       课程空间与教学班的绑定 ID；未绑定课程空间时可为 null
 * @param courseSpaceId   课程空间 ID；未绑定时可为 null
 * @param organizationId  组织 ID
 * @param className       教学班名称，可为 null
 * @param courseName      课程名称，可为 null
 * @param studentCount    有效学生数；null 表示未提供，不是 0
 */
public record CourseScope(
        Long teachingClassId,
        Long bindingId,
        Long courseSpaceId,
        Long organizationId,
        String className,
        String courseName,
        Long studentCount)
{
    /**
     * 校验必要标识。
     *
     * @throws OverviewValidationException 教学班或组织 ID 为空，或学生数为负数
     */
    public CourseScope
    {
        if (teachingClassId == null)
        {
            throw new OverviewValidationException("teachingClassId 不能为空");
        }
        if (organizationId == null)
        {
            throw new OverviewValidationException("organizationId 不能为空");
        }
        if (studentCount != null && studentCount < 0)
        {
            throw new OverviewValidationException("studentCount 不能为负数");
        }
    }
}
