package com.ruoyi.education.course.api;

import com.ruoyi.common.exception.ServiceException;

/**
 * 管理员授权组织内的一条教学班—课程绑定范围。
 *
 * <p>口径与总览管理员范围一致：一个当前有效
 * 课程绑定一条；没有绑定的教学班保留一条 bindingId/courseSpaceId 为 null 的
 * 记录。同一教学班多个绑定会重复出现，调用方不得按 teachingClassId 去重，
 * 除非产品明确改变口径。</p>
 *
 * <p>studentCount 是该教学班当前有效学生数，按
 * {@code [joined_at, left_at)} 计算；同一教学班多个绑定会重复该人数，与学生数
 * 总览的“按范围求和、未跨班去重”口径一致。</p>
 *
 * @param organizationId 教学班所属组织 ID
 * @param teachingClassId 教学班 ID
 * @param className      教学班名称
 * @param classCode      教学班编码
 * @param classStatus    教学班状态；当前契约只返回 ACTIVE
 * @param bindingId      当前有效课程绑定 ID；未绑定时为 null
 * @param courseSpaceId  课程空间 ID；未绑定时为 null
 * @param courseName     课程名称；未绑定或课程不存在时可为 null
 * @param studentCount   当前有效学生数，非负
 */
public record EduAdminOrganizationClassRange(
        Long organizationId,
        Long teachingClassId,
        String className,
        String classCode,
        String classStatus,
        Long bindingId,
        Long courseSpaceId,
        String courseName,
        Long studentCount)
{
    /**
     * 校验范围行。
     *
     * @throws ServiceException 标识为空、学生数为负或绑定字段不成对
     */
    public EduAdminOrganizationClassRange
    {
        if (organizationId == null || organizationId <= 0L)
        {
            throw new ServiceException("organizationId 必须为正整数");
        }
        if (teachingClassId == null || teachingClassId <= 0L)
        {
            throw new ServiceException("teachingClassId 必须为正整数");
        }
        if (className == null || className.isBlank())
        {
            throw new ServiceException("教学班名称不能为空");
        }
        if (classCode == null || classCode.isBlank())
        {
            throw new ServiceException("教学班编码不能为空");
        }
        if (classStatus == null || classStatus.isBlank())
        {
            throw new ServiceException("教学班状态不能为空");
        }
        if ((bindingId == null) != (courseSpaceId == null))
        {
            throw new ServiceException("bindingId 与 courseSpaceId 必须同时为空或同时存在");
        }
        if (studentCount == null || studentCount < 0L)
        {
            throw new ServiceException("studentCount 不能为空或负数");
        }
    }
}
