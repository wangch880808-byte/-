package com.ruoyi.education.overview.dto;

import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 教师或学生授权范围中的一条明细。
 *
 * <p>身份和范围由服务端从认证上下文和 {@code CourseScopePort} 取得；本响应只
 * 暴露教学班、绑定、课程和已由来源提供的学生数，不包含学生名单。ID 使用字符串，
 * 避免前端对 19 位 Long 产生精度损失。{@code studentCount} 为 null 表示来源未提供，
 * 不是 0。</p>
 *
 * @param teachingClassId 教学班 ID，正整数字符串
 * @param bindingId       课程与教学班绑定 ID，可为 null
 * @param courseSpaceId   课程空间 ID，可为 null
 * @param className       教学班名称，可为 null
 * @param courseName      课程名称，可为 null
 * @param studentCount    来源提供的有效学生数，可为 null
 */
public record ScopeRangeResponse(
        String teachingClassId,
        String bindingId,
        String courseSpaceId,
        String className,
        String courseName,
        Long studentCount)
{
    /**
     * 校验明细字段。
     *
     * @throws OverviewValidationException 教学班 ID 为空或学生数为负数
     */
    public ScopeRangeResponse
    {
        if (teachingClassId == null || teachingClassId.isBlank())
        {
            throw new OverviewValidationException("teachingClassId 不能为空");
        }
        if (studentCount != null && studentCount < 0)
        {
            throw new OverviewValidationException("studentCount 不能为负数");
        }
    }
}
